use jni::errors::Result as JniResult;
use jni::objects::JThrowable;
use jni::JNIEnv;

use crate::android::jni_app::prelude::*;
use crate::comics::prelude::GetComicImageError;

impl AsJThrowable for GetComicImageError {
    fn as_throwable<'a>(&self, env: &'a JNIEnv) -> JniResult<JThrowable<'a>> {
        use self::GetComicImageError::*;
        use app_objects::error::NativeException;

        let code = match self {
            CantFind => NativeException::Code::ContainerCantFindFile,
            OpenError(_) | EmptyFile => NativeException::Code::ImageOpen,
        };

        NativeException::new(env, code, self.as_jni_error_msg())
    }
}
