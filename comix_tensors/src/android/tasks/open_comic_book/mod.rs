mod error;

pub use self::error::ComicBookOpenError;

use crate::android::jni_app::{ComicBookData, ComicOpenCallback, JniCache};
use crate::comics::prelude::*;
use crate::futures_ext::cancel::cancel_channel;
use crate::futures_ext::error_listener::ErrorListenerFuture;
use crate::futures_ext::*;

use std::os::unix::io::RawFd;
use std::sync::Arc;
use std::thread::current as current_thread;

use futures_locks::Mutex;

use jni::errors::Error as JniError;
use jni::objects::{GlobalRef, JObject};
use jni::{JNIEnv, JavaVM};

use tokio::prelude::*;

///Start Tokio task which open comic container by it [fd], extract files from it, filter,
/// and returns supported files data
/// Return error if any goes wrong or task was cancelled using [cancel_receiver]
pub fn open_comic_book(
    env: &JNIEnv,
    jni_cache: Arc<JniCache<'static>>,
    fd: RawFd,
    callback: JObject,
) -> Result<(), ComicBookOpenError> {
    let vm = Arc::new(env.get_java_vm()?);
    let callback = env.new_global_ref(callback)?;

    let (cancel_sender, mut cancel_receiver) = cancel_channel();
    if let Err(e) = env.set_rust_field(callback.as_obj(), "id", cancel_sender) {
        panic!(
            "Can't set cancel sender pointer to Java object. =>>> {:?}",
            e
        );
    }

    //used as sender of inner errors
    let error_mutex = Mutex::new(None);

    //add error listener as additional cancel future
    //it will cancel root task if any inner return error
    cancel_receiver.additional_cancel(ErrorListenerFuture::new(error_mutex.clone()));

    //build final cancel future and share it for Clone functionality
    let cancel_receiver = cancel_receiver.build().shared();

    let comic_files_future = ComicContainerVariant::init(fd)
        .from_err::<ComicBookOpenError>()
        .map(|container| container.files().from_err::<ComicBookOpenError>())
        .flatten_stream()
        .cancellable(cancel_receiver)
        .get_comic_files()
        .fold((Vec::new(), None), |mut acc, comic_file| {
            //collect all pages and comicInfo if present
            let (pages, comic_info) = &mut acc;

            use ComicFile::*;

            match comic_file {
                ComicPage(mut comic_page_content) => {
                    pages.push((comic_page_content.pos, comic_page_content.name));
                }
                ComicInfo(new_info) => {
                    comic_info.replace(new_info);
                }
            }

            future::ok::<_, ComicBookOpenError>(acc)
        })
        .and_then(|comic_book_data| {
            on_comic_book_opened(vm, jni_cache, callback, comic_book_data).from_err()
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

fn on_comic_book_opened(
    vm: Arc<JavaVM>,
    jni_cache: Arc<JniCache<'static>>,
    callback: GlobalRef,
    comic_book_data: ComicBookData,
) -> impl Future<Item = (), Error = JniError> {
    future::lazy(move || {
        let env = vm.attach_current_thread()?;

        debug!(
            "Trying to send comic book data via JNI. Thread: {:?}",
            current_thread().name()
        );

        callback.call_on_comic_opened(&env, &jni_cache, &comic_book_data)?;

        Ok(())
    })
}
