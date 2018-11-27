mod error;
mod process_comic_page;
mod send_comic_info;

pub use self::error::ProcessError;

use self::process_comic_page::*;
use self::send_comic_info::*;

use crate::android::jni_app::JniCache;
use crate::comics::prelude::*;
use crate::futures_ext::cancel::cancel_channel;
use crate::futures_ext::error_listener::ErrorListenerFuture;
use crate::futures_ext::*;
use crate::Config;

use std::os::unix::io::RawFd;
use std::sync::Arc;

use futures_locks::Mutex;
use jni::objects::JObject;
use jni::JNIEnv;
use tokio::prelude::*;

///Start Tokio task which open comic container by it [fd], extract files from it, filter,
/// preprocess, send to the TF interpreter via JNI [callback], get it back and decode result
/// Return error if any goes wrong or task was cancelled using [cancel_receiver]
pub fn process(
    env: &JNIEnv,
    jni_cache: Arc<JniCache<'static>>,
    fd: RawFd,
    callback: JObject,
    cfg: Arc<Config<'static>>,
) -> Result<(), ProcessError> {
    let vm = Arc::new(env.get_java_vm()?);
    let callback = env.new_global_ref(callback)?;

    let (cancel_sender, mut cancel_receiver) = cancel_channel();
    if let Err(e) = env.set_rust_field(callback.as_obj(), "id", cancel_sender) {
        panic!("Can't set cancel sender pointer to Java object. =>>> {:?}", e);
    }

    //used as sender of inner errors
    let error_mutex = Mutex::new(None);

    //add error listener as additional cancel future
    //it will cancel root task if any inner return error
    cancel_receiver.additional_cancel(ErrorListenerFuture::new(error_mutex.clone()));

    //build final cancel future and share it for Clone functionality
    let cancel_receiver = cancel_receiver.build().shared();

    //used inside for_each function
    let error_mutex_inner = error_mutex.clone();

    let comic_files_future = ComicContainerVariant::init(fd)
        .from_err::<ProcessError>()
        .map(|container| container.files().from_err::<ProcessError>())
        .flatten_stream()
        .cancellable(cancel_receiver.clone())
        .preprocess_comic_files(cfg.interpreter_input_shape())
        .for_each(move |comic_file| {
            vm.attach_current_thread()?;

            let callback = callback.clone();
            let cfg = Arc::clone(&cfg);
            let vm = Arc::clone(&vm);
            let jni_cache = Arc::clone(&jni_cache);

            let error_mutex_inner = error_mutex_inner.clone();

            //determine type of the extracted file from comic container
            match comic_file {
                ComicFile::Image(interpreter_input) => {
                    let f = process_comic_page(vm, jni_cache, interpreter_input, callback, cfg)
                        .from_err::<ProcessError>()
                        .cancellable(cancel_receiver.clone())
                        .send_error(error_mutex_inner);

                    tokio::spawn(f.map(|_| ()).map_err(|_| ()));
                }
                ComicFile::ComicInfo(info) => {
                    let f = send_comic_info(vm, jni_cache, callback, info)
                        .from_err::<ProcessError>()
                        .cancellable(cancel_receiver.clone())
                        .send_error(error_mutex_inner);

                    tokio::spawn(f.map(|_| ()).map_err(|_| ()));
                }
            }

            Ok(())
        })
        .send_error(error_mutex.clone());

    tokio::run(comic_files_future.map(|_| ()).map_err(|_| ()));

    //extract error from mutex, if any
    match error_mutex.try_unwrap() {
        Ok(Some(error)) => Err(error),
        Ok(None) => Ok(()),
        _ => unreachable!(),
    }
}
