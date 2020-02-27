mod constants;
mod objects;
mod utils;

pub mod callback {
    use jni::errors::Result as JniResult;
    use jni::objects::{JObject, JThrowable, JValue};
    use jni::JNIEnv;

    ///Send task [result] Java object via provided [callback]
    pub fn send_task_result(env: &JNIEnv, callback: JObject, result: JObject) -> JniResult<()> {
        env.call_method(
            callback,
            "taskResult",
            "(Ljava/lang/Object;)V",
            &[result.into()],
        )
        .map(|_| ())
    }

    ///Send task [error] Java object via provided [callback]
    pub fn send_task_error(env: &JNIEnv, callback: JObject, error: JThrowable) -> JniResult<()> {
        env.call_method(
            callback,
            "taskError",
            "(Ljava/lang/Throwable;)V",
            &[(JValue::Object(*error))],
        )
        .map(|_| ())
    }
}

pub mod throw {
    use jni::descriptors::Desc;
    use jni::errors::Result as JniResult;
    use jni::objects::{JClass, JThrowable};
    use jni::strings::JNIString;
    use jni::JNIEnv;

    use super::constants;
    use super::utils::prelude::*;

    ///Throw a Java's IllegalArgumentException with provided message
    /// Return true if exception thrown
    /// Call fatal error if any error occured
    pub fn throw_illegal_argument_exception(env: &JNIEnv, msg: impl Into<JNIString>) -> bool {
        base_throw_exception(
            env,
            (constants::Errors::JAVA_ILLEGAL_ARGUMENT_EXCEPTION_TYPE, msg),
        )
        .jni_fatal_unwrap(env, || "Can't send IllegalArgumentException via JNI")
    }

    ///Throw NativeFatalError
    /// Return true if error thrown
    /// Call fatal error if any error occured
    pub fn throw_native_fatal_error(env: &JNIEnv, msg: impl Into<JNIString>) -> bool {
        use super::objects::app::error::NativeFatalError;

        NativeFatalError::new(env, msg)
            .and_then(|throwable| base_throw_exception(env, throwable))
            .jni_fatal_unwrap(env, || "Can't send NativeFatalError via JNI")
    }

    fn base_throw_exception<'a>(
        env: &JNIEnv<'a>,
        class: impl ThrowExceptionType<'a>,
    ) -> JniResult<bool> {
        env.exception_check().and_then(|has_exception| {
            if !has_exception {
                class.throw(env).map(|_| true)
            } else {
                Ok(false)
            }
        })
    }

    trait ThrowExceptionType<'a> {
        fn throw(self, env: &JNIEnv<'a>) -> JniResult<()>;
    }

    impl<'a> ThrowExceptionType<'a> for JThrowable<'a> {
        fn throw(self, env: &JNIEnv<'a>) -> JniResult<()> {
            env.throw(self)
        }
    }

    impl<'a, T, M> ThrowExceptionType<'a> for (T, M)
    where
        T: Desc<'a, JClass<'a>>,
        M: Into<JNIString>,
    {
        fn throw(self, env: &JNIEnv<'a>) -> JniResult<()> {
            env.throw_new(self.0, self.1)
        }
    }
}

pub mod prelude {
    pub use super::callback;
    pub use super::objects::app as app_objects;
    pub use super::objects::init as init_class_loader;
    pub use super::objects::java as java_objects;
    pub use super::throw::{throw_illegal_argument_exception, throw_native_fatal_error};

    pub use super::utils::prelude::*;
}
