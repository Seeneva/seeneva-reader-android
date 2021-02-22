use jni::errors::Result as JniResult;
use jni::objects::JObject;
use jni::JNIEnv;
use once_cell::sync::Lazy;
use parking_lot::{Mutex, MutexGuard};

use super::field_pointer::{get_rust_field, set_rust_field, take_rust_field};
use super::{constants, JniClassConstructorCache};

pub mod Interpreter {
    use crate::comics::ml::PageYOLOInterpreter;

    use super::*;

    static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
        Lazy::new(|| (constants::INTERPRETER_TYPE, "()V").into());

    const INTERPRETER_PTR_FIELD: &str = "ptr";

    /// Create new JNI Interpreter object and set raw pointer to provided [interpreter]
    pub fn new<'a>(env: &'a JNIEnv, interpreter: PageYOLOInterpreter) -> JniResult<JObject<'a>> {
        let jni_interpreter = CONSTRUCTOR
            .init(env)
            .and_then(|constructor| constructor.create(&[]))?;

        set_rust_field(
            env,
            jni_interpreter,
            INTERPRETER_PTR_FIELD,
            Mutex::new(interpreter),
        )?;

        Ok(jni_interpreter)
    }

    /// Drop ML Interpreter wrapped in the [jni_interpreter]
    pub fn close(env: &JNIEnv, jni_interpreter: JObject) -> JniResult<()> {
        debug!(
            "Trying to cancel ML Interpreter: {:p}",
            jni_interpreter.into_inner()
        );

        take_rust_field::<_, _, Mutex<PageYOLOInterpreter>, _>(
            env,
            jni_interpreter,
            INTERPRETER_PTR_FIELD,
        )
        .map(|_| {
            debug!("Interpreter {:p} is closed", jni_interpreter.into_inner());

            ()
        })
    }

    /// Get Rust object from provided JNI object
    pub fn get_inner<'a>(
        env: &'a JNIEnv,
        jni_interpreter: JObject<'a>,
    ) -> JniResult<MutexGuard<'a, PageYOLOInterpreter>> {
        get_rust_field::<_, _, Mutex<PageYOLOInterpreter>, _>(
            env,
            jni_interpreter,
            INTERPRETER_PTR_FIELD,
        )
        .map(|field| field.lock())
    }
}

pub mod Tesseract {
    use tesseract_rs::Tesseract;

    use super::*;

    static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
        Lazy::new(|| (constants::TESSERACT_TYPE, "()V").into());

    const TESSERACT_PTR_FIELD: &str = "ptr";

    /// Create new JNI Tesseract object and set raw pointer to provided [`tesseract`]
    pub fn new<'a>(env: &'a JNIEnv, tesseract: Tesseract) -> JniResult<JObject<'a>> {
        let jni_tesseract = CONSTRUCTOR
            .init(env)
            .and_then(|constructor| constructor.create(&[]))?;

        set_rust_field(
            env,
            jni_tesseract,
            TESSERACT_PTR_FIELD,
            Mutex::new(tesseract),
        )?;

        Ok(jni_tesseract)
    }

    /// Drop Tesseract wrapped in the [jni_interpreter]
    pub fn close(env: &JNIEnv, jni_tesseract: JObject) -> JniResult<()> {
        debug!(
            "Trying to cancel Tesseract: {:p}",
            jni_tesseract.into_inner()
        );

        take_rust_field::<_, _, Mutex<Tesseract>, _>(env, jni_tesseract, TESSERACT_PTR_FIELD).map(
            |_| {
                debug!("Tesseract {:p} is closed", jni_tesseract.into_inner());

                ()
            },
        )
    }

    /// Get Rust Tesseract object from provided JNI object
    pub fn get_inner<'a>(
        env: &'a JNIEnv,
        jni_tesseract: JObject<'a>,
    ) -> JniResult<MutexGuard<'a, Tesseract>> {
        get_rust_field::<_, _, Mutex<Tesseract>, _>(
            env,
            jni_tesseract,
            TESSERACT_PTR_FIELD,
        ).map(|field| field.lock())
    }
}
