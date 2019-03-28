#![cfg(target_os = "android")]
#![allow(non_snake_case)]
mod jni_app;
mod model_config;
mod ndk;
mod tasks;

use self::model_config::ModelConfigJniWrapper;
use crate::futures_ext::cancel::CancelSignal;

use std::sync::Arc;
use std::panic;

use jni::objects::{JClass, JObject, JString};
use jni::sys::{jint, jobject};
use jni::{JNIEnv, JavaVM};

/// It will be called than library loaded for the first time
#[no_mangle]
pub unsafe extern "C" fn JNI_OnLoad(vm: JavaVM, _reserved: JObject) -> jint {
    use android_logger;
    use android_logger::Filter;

    android_logger::init_once(
        Filter::default().with_min_level(log::Level::Debug),
        Some("ComixReaderNative"),
    );

    match vm.get_env() {
        Ok(env) => {
            if let Err(e) = jni_app::init_cache(&env) {
                env.fatal_error(format!("Can't init JNI cache. =>>> {:?}", e));
            }
        }
        Err(e) => panic!("Can't get JniEnv to init JNI cache. =>>> {:?}", e)
    }

    //register error hook to get errors via JNI
    panic::set_hook(Box::new( move |panic_info|{
        match vm.get_env() {
            Ok(env) => env.fatal_error(format!("{}", panic_info)),
            Err(_) => panic!("{}", panic_info),
        };
    }));

    jni::sys::JNI_VERSION_1_6
}

// !!!!JNI_OnUnload will never be called on Android!!!!
//#[no_mangle]
//pub unsafe extern "C" fn JNI_OnUnload(vm: JavaVM, _reserved: jni::sys::jobject) {
//}

#[no_mangle]
pub unsafe extern "C" fn Java_com_almadevelop_comixreader_Model_cancelTask(
    env: JNIEnv,
    _: JClass,
    task_callback: JObject,
) {
    use tokio::sync::oneshot::Sender;

    match env.take_rust_field::<_, Sender<CancelSignal>>(task_callback, "id") {
        Ok(sender) => {
            if let Err(_) = sender.send(CancelSignal) {
                info!(
                    "Trying to cancel not actual task. It was dropped or finished for some reason"
                );
            }
        }
        Err(e) => {
            panic!("Can't take cancel sender from provided task callback. =>>> {:?}", e);
        }
    }
}

//TODO REMEMBER!!! output bytebuffer from interpretr FLOAT_BYTES_LEN * BATCH_SIZE * 9750 * 10 (4, 4,9750, 10)

///Open comic container file by [file_descriptor] and send results via JNI [callback]
#[no_mangle]
pub unsafe extern "C" fn Java_com_almadevelop_comixreader_Model_openComicBook(
    env: JNIEnv,
    _: JClass,
    file_descriptor: jint,
    callback: JObject,
) -> jobject {
    use self::tasks::open_comic_book::*;

    let jni_cache = jni_app::try_get_cache();

    let res = open_comic_book(&env, Arc::clone(&jni_cache), file_descriptor, callback);

    let res = match res {
        Ok(_) => jni_cache.comic_book_open_result.new_success(&env),
        Err(e) => jni_cache.comic_book_open_result.new_error(&env, &e),
    };

    match res {
        Ok(result_jobject) => result_jobject.into_inner(),
        Err(e) => env.fatal_error(format!(
            "Can't send comic files result via JNI. =>>>{:?}",
            e
        )),
    }
}

///Return model config from Android AssetsManager by its name [asset_file_name]
unsafe fn get_model_config(
    env: &JNIEnv,
    assets_manager: JObject,
    asset_file_name: JString,
) -> ModelConfigJniWrapper<'static> {
    let res = ModelConfigJniWrapper::open(&env, assets_manager, asset_file_name);
    match res {
        Ok(cfg) => cfg,
        Err(e) => panic!("Can't get model config. =>>> {:?}", e),
    }
}
