/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#![cfg(target_os = "android")]
#![allow(non_snake_case)]

use std::sync::Arc;

use jni::objects::{JClass, JObject, JString};
use jni::sys::{jboolean, jfloat, jint, jlong, jobject};
use jni::{JNIEnv, JavaVM};

use file_descriptor::FileRawFd;
use tasks::*;

use crate::utils::blake_hash;

use self::jni_app::*;

mod jni_app;
mod ndk;
mod tasks;

/// Check JNI input argument and throw IllegalArgumentException in case if [argument_expr] false
macro_rules! check_input_argument {
    ($argument_expr:expr, $msg:expr, $env:expr, $return:expr) => {
        if !$argument_expr {
            &$msg
                .throw(Throwable::IllegalArgumentException, &$env)
                .expect("Cannot throw JNI throwable");

            return $return;
        }
    };
}

/// Check if callback JObject is not null. Throw IllegalArgumentException if null.
macro_rules! check_callback_argument {
    ($callback:expr, $env:expr, $return:expr) => {
        check_input_argument!(
            !($callback.is_null()),
            "Callback cannot be null",
            $env,
            $return
        );
    };
}

/// Unwrap [Result] and throw JNI Error in case of [Result::Err]
macro_rules! unwrap_result {
    ($result:expr, $env:expr, $msg:literal, $return:expr) => {
        match $result {
            Ok(r) => r,
            Err(e) => {
                error!($msg);
                e.throw(Throwable::NativeFatalError, &$env)
                    .expect("Can't throw JNI NativeFatalError");
                return $return;
            }
        }
    };
}

/// It will be called than library loaded for the first time
#[no_mangle]
pub unsafe extern "C" fn JNI_OnLoad(vm: JavaVM, _reserved: JObject) -> jint {
    // set Android logger
    {
        use android_logger::Config;

        let config = Config::default()
            .with_min_level(if cfg!(feature = "debug_logs") {
                log::Level::Debug
            } else {
                log::Level::Info
            })
            .with_tag("ComicsReaderNative");

        android_logger::init_once(config);
    }

    std::panic::set_hook(Box::new(|panic_info| {
        if let Some(s) = panic_info.payload().downcast_ref::<String>() {
            error!("Panic occurred: {}", s);
        } else if let Some(s) = panic_info.payload().downcast_ref::<&str>() {
            error!("Panic occurred: {}", s);
        } else {
            error!("Unknown panic occurred");
        }
    }));

    let vm = Arc::new(vm);

    let env = vm.get_env().expect("Get JNI env");

    init_class_loader(&env);

    // set leptonica error handler
    {
        use tesseract_rs::leptonica::set_error_handler as set_lept_error_handler;

        unsafe extern "C" fn handler(msg: *const std::os::raw::c_char) {
            let msg = std::ffi::CStr::from_ptr(msg);

            if let Ok(msg) = msg.to_str() {
                error!("Leptonica error message: '{}'", msg);
            }
        }

        set_lept_error_handler(Some(handler));
    }

    jni::sys::JNI_VERSION_1_6
}

// !!!!JNI_OnUnload will never be called on Android!!!!
// #[no_mangle]
// pub unsafe extern "C" fn JNI_OnUnload(vm: JavaVM, _reserved: JObject) {
// }

///Cancel provided task [task]
/// Return is task was cancelled
#[no_mangle]
pub unsafe extern "C" fn Java_app_seeneva_reader_data_source_jni_Native_00024Task_cancelNative(
    env: JNIEnv,
    task: JObject,
) -> jboolean {
    check_input_argument!(!task.is_null(), "Task cannot be null", env, 0 as _);

    unwrap_result!(
        app_objects::task::cancel(&env, task),
        env,
        "Can't cancel task",
        0 as _
    );

    jni::sys::JNI_TRUE
}

/// Init Machine Learning Interpreter from Android Asset by TF Lite model [file_name]
#[no_mangle]
pub unsafe extern "C" fn Java_app_seeneva_reader_data_source_jni_Native_initInterpreterFromAsset(
    env: JNIEnv,
    _: JClass,
    asset_mgr: JObject,
    file_name: JString,
    callback: JObject,
) -> jobject {
    check_input_argument!(
        !asset_mgr.is_null(),
        "AssetManager cannot be null",
        env,
        0 as _
    );
    check_input_argument!(
        !file_name.is_null(),
        "ML Model file name cannot be null",
        env,
        0 as _
    );
    check_callback_argument!(callback, env, 0 as _);

    unwrap_result!(
        init_interpreter_from_asset(&env, asset_mgr, file_name, callback),
        env,
        "Can't init interpreter",
        0 as _
    )
    .into_inner()
}

/// Init Tesseract from Android Asset by *.tessdata [`tessdata_file_name`]
#[no_mangle]
pub unsafe extern "C" fn Java_app_seeneva_reader_data_source_jni_Native_initTesseractFromAsset(
    env: JNIEnv,
    _: JClass,
    asset_mgr: JObject,
    tessdata_file_name: JString,
    tess_lang: JString,
    callback: JObject,
) -> jobject {
    check_input_argument!(
        !asset_mgr.is_null(),
        "AssetManager cannot be null",
        env,
        0 as _
    );
    check_input_argument!(
        !tessdata_file_name.is_null(),
        "Tessdata file name cannot be null",
        env,
        0 as _
    );
    check_input_argument!(
        !tess_lang.is_null(),
        "Tessdata language cannot be null",
        env,
        0 as _
    );
    check_callback_argument!(callback, env, 0 as _);

    unwrap_result!(
        init_tesseract_from_asset(&env, asset_mgr, tessdata_file_name, tess_lang, callback),
        env,
        "Can't init Tesseract",
        0 as _
    )
    .into_inner()
}

/// Close ML Interpreter
#[no_mangle]
pub unsafe extern "C" fn Java_app_seeneva_reader_data_entity_ml_Interpreter_closeNative(
    env: JNIEnv,
    interpreter: JObject,
) {
    check_input_argument!(
        !interpreter.is_null(),
        "Interpreter cannot be null",
        env,
        ()
    );

    unwrap_result!(
        app_objects::ml::Interpreter::close(&env, interpreter),
        env,
        "Can't close Interpreter object",
        ()
    );
}

/// Close ML Interpreter
#[no_mangle]
pub unsafe extern "C" fn Java_app_seeneva_reader_data_entity_ml_Tesseract_closeNative(
    env: JNIEnv,
    tesseract: JObject,
) {
    check_input_argument!(!tesseract.is_null(), "Tesseract cannot be null", env, ());

    unwrap_result!(
        app_objects::ml::Tesseract::close(&env, tesseract),
        env,
        "Can't close Tesseract object",
        ()
    );
}

///Open comic container file by [file_descriptor] and send results via JNI [callback]
/// Task used to set pointer to cancel feature
/// Will block the thread until finished
#[no_mangle]
pub unsafe extern "C" fn Java_app_seeneva_reader_data_source_jni_Native_openComicBook(
    env: JNIEnv,
    _: JClass,
    file_descriptor: jint,
    file_path: JString,
    comic_book_name: JString,
    comic_book_direction: jint,
    ml_interpreter: JObject,
    callback: JObject,
) -> jobject {
    let file_descriptor = FileRawFd::new(file_descriptor);

    check_input_argument!(
        !file_path.is_null(),
        "File path cannot be null",
        env,
        0 as _
    );
    check_input_argument!(
        !comic_book_name.is_null(),
        "Comic book name cannot be null",
        env,
        0 as _
    );
    check_input_argument!(
        !ml_interpreter.is_null(),
        "ML Interpreter cannot be null",
        env,
        0 as _
    );
    check_callback_argument!(callback, env, 0 as _);

    let result = comic_book_metadata(
        &env,
        file_descriptor,
        file_path,
        comic_book_name,
        comic_book_direction,
        ml_interpreter,
        callback,
    );

    unwrap_result!(result, env, "Can't open comic book", 0 as _).into_inner()
}

#[no_mangle]
pub unsafe extern "C" fn Java_app_seeneva_reader_data_source_jni_Native_getComicFileData(
    env: JNIEnv,
    _: JClass,
    file_descriptor: jint,
) -> jobject {
    let mut file_descriptor = FileRawFd::new(file_descriptor);

    let (size, hash) = unwrap_result!(
        blake_hash(&mut file_descriptor),
        env,
        "Can't calculate comic book hash",
        0 as _
    );

    unwrap_result!(
        app_objects::file_hash::new(&env, size, &hash),
        env,
        "Can't map comic book hash into JNI object",
        0 as _
    )
    .into_inner()
}

#[no_mangle]
pub unsafe extern "C" fn Java_app_seeneva_reader_data_source_jni_Native_getPageImageData(
    env: JNIEnv,
    _: JClass,
    file_descriptor: jint,
    image_position: jlong,
    callback: JObject,
) -> jobject {
    let file_descriptor = FileRawFd::new(file_descriptor);

    check_input_argument!(
        image_position >= 0,
        "Image position can't be negative",
        env,
        0 as _
    );
    check_callback_argument!(callback, env, 0 as _);

    let result = get_comic_book_encoded_image(&env, file_descriptor, image_position as _, callback);

    unwrap_result!(result, env, "Can't get encoded comic book page", 0 as _).into_inner()
}

/// Decode `encoded_page`
/// Optionally image can be resized using (width, height) from target Android `bitmap`
/// Resize type depends on `resize_fast`
/// Optionally image can be cropped using (x, y, width, height) array `crop`
#[no_mangle]
pub unsafe extern "C" fn Java_app_seeneva_reader_data_source_jni_Native_decodePage(
    env: JNIEnv,
    _: JClass,
    encoded_page: JObject,
    bitmap: JObject,
    crop: JObject,
    resize_fast: jboolean,
    callback: JObject,
) -> jobject {
    check_input_argument!(
        !encoded_page.is_null(),
        "ComicPageImageData can't null",
        env,
        0 as _
    );
    check_input_argument!(!bitmap.is_null(), "Android bitmap can't null", env, 0 as _);
    check_callback_argument!(callback, env, 0 as _);

    let result = decode_img(&env, encoded_page, bitmap, crop, resize_fast, callback);

    unwrap_result!(result, env, "Can't decode comic book page", 0 as _).into_inner()
}

/// Close provided encoded comic book page
#[no_mangle]
pub unsafe extern "C" fn Java_app_seeneva_reader_data_entity_ComicPageImageData_closeNative(
    env: JNIEnv,
    encoded_page: JObject,
) {
    check_input_argument!(
        !encoded_page.is_null(),
        "ComicEncodedPage cannot be null",
        env,
        ()
    );

    let result = app_objects::comic_page_image_data::close(&env, encoded_page);

    unwrap_result!(result, env, "Can't close ComicEncodedPage", ())
}

/// Recognise text on provided Android `bitmap` using provided `tesseract` instace
/// `word_min_conf` is a required minimal confidence for each result word
/// Callback will return text as Java's String in case of success
#[no_mangle]
pub unsafe extern "C" fn Java_app_seeneva_reader_data_source_jni_Native_recogniseText(
    env: JNIEnv,
    _: JClass,
    tesseract: JObject,
    bitmap: JObject,
    word_min_conf: jfloat,
    callback: JObject,
) -> jobject {
    check_input_argument!(!tesseract.is_null(), "Tesseract can't null", env, 0 as _);
    check_input_argument!(!bitmap.is_null(), "Android bitmap can't null", env, 0 as _);
    check_input_argument!(
        word_min_conf >= 0. && word_min_conf <= 1.,
        "Word confidence should be in [0.0, 1.0]",
        env,
        0 as _
    );
    check_callback_argument!(callback, env, 0 as _);

    let task = recognise_bitmap(&env, tesseract, bitmap, word_min_conf, callback);

    unwrap_result!(task, env, "Can't close ComicEncodedPage", 0 as _).into_inner()
}
