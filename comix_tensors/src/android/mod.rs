#![cfg(target_os = "android")]
#![allow(non_snake_case)]

use std::any::Any;
use std::fs::File;
use std::os::unix::io::FromRawFd;
use std::panic::AssertUnwindSafe;
use std::sync::Arc;

use jni::objects::{JClass, JObject, JString};
use jni::sys::{jboolean, jint, jlong, jobject};
use jni::{JNIEnv, JavaVM};
use tokio::prelude::*;

use tasks::prelude::*;

use crate::comics::prelude::archive_hash_metadata;

use self::jni_app::prelude::*;

mod jni_app;
//mod model_config;
//mod ndk;
mod tasks;

/// It will be called than library loaded for the first time
#[no_mangle]
pub unsafe extern "C" fn JNI_OnLoad(vm: JavaVM, _reserved: JObject) -> jint {
    {
        use android_logger::Config;

        let config = Config::default()
            .with_min_level(log::Level::Debug)
            .with_tag("ComicsReaderNative");

        android_logger::init_once(config);
    }

    let vm = Arc::new(vm);

    let env = vm.get_env().expect("Get JNI env");

    init_class_loader(&env);

    crate::init_future_runtime(|builder| {
        builder.panic_handler(jni_panic_handler(Arc::clone(&vm)));
    })
    .jni_fatal_unwrap(&env, || "Can't init futures runtime");

    jni::sys::JNI_VERSION_1_6
}

// !!!!JNI_OnUnload will never be called on Android!!!!
//#[no_mangle]
//pub unsafe extern "C" fn JNI_OnUnload(vm: JavaVM, _reserved: jni::sys::jobject) {
//}

///Cancel provided task [task]
/// Return is task was cancelled
#[no_mangle]
pub unsafe extern "C" fn Java_com_almadevelop_comixreader_data_source_jni_Native_00024Task_cancelNative(
    env: JNIEnv,
    task: JObject,
) -> jboolean {
    future::lazy(|| cancel_task(&env, task).map_err(|e| AssertUnwindSafe(e)))
        .catch_unwind()
        .wait()
        .and_then(|result| result.map_err(|AssertUnwindSafe(e)| Box::new(e) as Box<dyn Any + Send>))
        .map_err(|e| e.into_jni_error_wrapper())
        .jni_error_unwrap(&env, || "Can't cancel task", || false) as _
}

//TODO REMEMBER!!! output bytebuffer from interpretr FLOAT_BYTES_LEN * BATCH_SIZE * 9750 * 10 (4, 4,9750, 10)

///Open comic container file by [file_descriptor] and send results via JNI [callback]
/// Task used to set pointer to cancel feature
/// Will block the thread until finished
#[no_mangle]
pub unsafe extern "C" fn Java_com_almadevelop_comixreader_data_source_jni_Native_openComicBook(
    env: JNIEnv,
    _: JClass,
    file_descriptor: jint,
    file_path: JString,
    comic_book_name: JString,
    callback: JObject,
) -> jobject {
    if file_descriptor < 0 {
        throw_illegal_argument_exception(&env, "File descriptor is negative");

        return JObject::null().into_inner();
    }

    if callback.is_null() {
        throw_illegal_argument_exception(&env, "Callback cannot be null");

        return JObject::null().into_inner();
    }

    if file_path.is_null() {
        throw_illegal_argument_exception(&env, "File path cannot be null");

        return JObject::null().into_inner();
    }

    if comic_book_name.is_null() {
        throw_illegal_argument_exception(&env, "Comic book name cannot be null");

        return JObject::null().into_inner();
    }

    future::lazy(|| {
        comic_book_metadata_task(&env, file_descriptor, file_path, comic_book_name, callback)
            .map_err(|e| AssertUnwindSafe(e))
    })
    .catch_unwind()
    .wait()
    .and_then(|result| result.map_err(|AssertUnwindSafe(e)| e.into_any()))
    .map_err(|e| e.into_jni_error_wrapper())
    .jni_error_unwrap(&env, || "Can't get comic book metadata", || JObject::null())
    .into_inner()
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_almadevelop_comixreader_data_source_jni_Native_getComicFileData(
    env: JNIEnv,
    _: JClass,
    file_descriptor: jint,
) -> jobject {
    if file_descriptor < 0 {
        throw_illegal_argument_exception(&env, "File descriptor is negative");

        return JObject::null().into_inner();
    }

    future::lazy(|| {
        archive_hash_metadata(&mut (File::from_raw_fd(file_descriptor) as File))
            .map_err(|e| Box::new(e) as Box<dyn Any + Send>)
            .and_then(|(size, hash)| {
                app_objects::file_hash::new(&env, size, &hash)
                    .map_err(|e| Box::new(e) as Box<dyn Any + Send>)
            })
            .map_err(|e| AssertUnwindSafe(e))
    })
    .catch_unwind()
    .wait()
    .and_then(|result| result.map_err(|AssertUnwindSafe(e)| e))
    .map_err(|e| e.into_jni_error_wrapper())
    .jni_error_unwrap(&env, || "Can't get comic file data", || JObject::null())
    .into_inner()
}

///Get the image from the comic archive which opens by it [file_descriptor]
#[no_mangle]
pub unsafe extern "C" fn Java_com_almadevelop_comixreader_data_source_jni_Native_getImage(
    env: JNIEnv,
    _: JClass,
    file_descriptor: jint,
    image_position: jlong,
    callback: JObject,
) -> jobject {
    if image_position < 0 {
        throw_illegal_argument_exception(&env, "Image position can't be negative");

        return JObject::null().into_inner();
    }

    future::lazy(|| {
        get_comic_book_image_task(
            &env,
            file_descriptor,
            image_position as _,
            ExtractImageType::Default,
            callback,
        )
        .map_err(|e| AssertUnwindSafe(e))
    })
    .catch_unwind()
    .wait()
    .and_then(|result| result.map_err(|AssertUnwindSafe(e)| e.into_any()))
    .map_err(|e| e.into_jni_error_wrapper())
    .jni_error_unwrap(&env, || "Can't get comic book image", || JObject::null())
    .into_inner()
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_almadevelop_comixreader_data_source_jni_Native_getImageThumbnail(
    env: JNIEnv,
    _: JClass,
    file_descriptor: jint,
    image_position: jlong,
    image_width: jint,
    image_height: jint,
    callback: JObject,
) -> jobject {
    if image_position < 0 {
        throw_illegal_argument_exception(&env, "Image position can't be negative");

        return JObject::null().into_inner();
    }

    if image_width < 0 || image_height < 0 {
        throw_illegal_argument_exception(
            &env,
            format!(
                "Width and height cannot be negative. Width {}, height {}",
                image_width, image_height
            ),
        );

        return JObject::null().into_inner();
    }

    future::lazy(|| {
        get_comic_book_image_task(
            &env,
            file_descriptor,
            image_position as _,
            ExtractImageType::Thumbnail((image_width as _, image_height as _)),
            callback,
        )
        .map_err(|e| AssertUnwindSafe(e))
    })
    .catch_unwind()
    .wait()
    .and_then(|result| result.map_err(|AssertUnwindSafe(e)| e.into_any()))
    .map_err(|e| e.into_jni_error_wrapper())
    .jni_error_unwrap(
        &env,
        || "Can't get comic book image thumbnail",
        || JObject::null(),
    )
    .into_inner()
}

/////Return model config from Android AssetsManager by its name [asset_file_name]
//unsafe fn get_model_config(
//    env: &JNIEnv,
//    assets_manager: JObject,
//    asset_file_name: JString,
//) -> ModelConfigJniWrapper<'static> {
//    let res = ModelConfigJniWrapper::open(&env, assets_manager, asset_file_name);
//    match res {
//        Ok(cfg) => cfg,
//        Err(e) => panic!("Can't get model config. =>>> {:?}", e),
//    }
//}
