use jni::errors::Result as JniResult;
use jni::objects::JThrowable;
use jni::strings::JNIString;
use jni::JNIEnv;
use once_cell::sync::Lazy;

use super::{constants, JniClassConstructorCache};

pub mod IllegalArgumentException {
    use super::*;

    static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> = Lazy::new(|| {
        (
            constants::Errors::JAVA_ILLEGAL_ARGUMENT_EXCEPTION_TYPE,
            "(Ljava/lang/String;)V",
        )
            .into()
    });

    pub fn new<'a>(env: &'a JNIEnv, msg: impl Into<JNIString>) -> JniResult<JThrowable<'a>> {
        let msg = env.auto_local(env.new_string(msg)?);

        CONSTRUCTOR
            .init(env)
            .and_then(|constructor| constructor.create(&[(&msg).into()]))
            .map(Into::into)
    }
}
