use std::any::Any;
use std::error::Error;

use jni::errors::Result as JniResult;
use jni::objects::JThrowable;
use jni::JNIEnv;

use crate::android::jni_app::prelude::*;
use crate::comics::prelude::{ComicContainerError, InitComicContainerError};
use std::ops::Deref;

pub type TaskError = Box<dyn AsJThrowable + Send>;

impl AsJThrowable for Box<dyn ComicContainerError> {
    fn as_throwable<'a>(&self, env: &'a JNIEnv) -> JniResult<JThrowable<'a>> {
        use app_objects::error::NativeException;

        NativeException::new(
            env,
            NativeException::Code::ContainerRead,
            self.deref().into_jni_error_wrapper().as_jni_error_msg(),
        )
    }
}

impl AsJThrowable for InitComicContainerError {
    fn as_throwable<'a>(&self, env: &'a JNIEnv) -> JniResult<JThrowable<'a>> {
        use app_objects::error::NativeException;
        use InitComicContainerError::*;

        let code = match self {
            UnsupportedType(_) => NativeException::Code::ContainerOpenUnsupported,
            Magic(magic_err) => {
                use crate::MagicTypeError::*;

                match magic_err {
                    IO(_) => NativeException::Code::ContainerOpenMagicIo,
                    UnknownFormat => NativeException::Code::ContainerOpenUnknownFormat,
                }
            }
        };

        NativeException::new(env, code, self.as_jni_error_msg())
    }
}

///Error of init async task
pub struct InitTaskError<T>(Box<T>)
where
    T: ?Sized;

impl InitTaskError<dyn Error + Send> {
    ///Convert to a Box of Any
    pub fn into_any(self) -> Box<dyn Any + Send> {
        Box::new(self.0) as Box<_>
    }
}

impl<T> From<T> for InitTaskError<dyn Error + Send>
where
    T: Error + Send + 'static,
{
    fn from(inner: T) -> Self {
        InitTaskError(Box::new(inner))
    }
}
