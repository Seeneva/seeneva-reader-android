use jni::errors::Result as JniResult;
use jni::objects::JThrowable;
use jni::JNIEnv;

use crate::android::jni_app::prelude::*;
use crate::comics::prelude::CalcArchiveHashError;
use crate::GetComicFileMetadataError;

impl AsJThrowable for GetComicFileMetadataError {
    fn as_throwable<'a>(&self, env: &'a JNIEnv) -> JniResult<JThrowable<'a>> {
        use self::GetComicFileMetadataError::*;
        use app_objects::error::NativeException;

        let code = match self {
            NoComicPages => NativeException::Code::EmptyBook,
            CantOpenImage(_) => NativeException::Code::ImageOpen,
        };

        NativeException::new(env, code, self.as_jni_error_msg())
    }
}

impl AsJThrowable for CalcArchiveHashError {
    fn as_throwable<'a>(&self, env: &'a JNIEnv) -> JniResult<JThrowable<'a>> {
        as_jni_fatal_error(env, self.into_jni_error_wrapper())
    }
}
