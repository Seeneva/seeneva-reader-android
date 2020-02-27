use jni::errors::Result as JniResult;
use jni::objects::{JClass, JObject, JThrowable};
use jni::strings::JNIString;
use jni::JNIEnv;
use once_cell::sync::OnceCell;

use super::{constants::*, try_cache_class_descr, CacheClassDescr};

///Represent any fatal error
pub mod NativeFatalError {
    use super::*;

    static CLASS_DESCR: OnceCell<CacheClassDescr> = OnceCell::new();

    ///Create a new one error
    pub fn new<'a>(env: &JNIEnv<'a>, msg: impl Into<JNIString>) -> JniResult<JThrowable<'a>> {
        let msg = env.new_string(msg)?;

        let CacheClassDescr(ref class, ref constructor) = class_instance(env);

        env.new_object_unchecked(
            JClass::from(class.as_obj()),
            *constructor,
            &[JObject::from(msg).into()],
        )
        .map(Into::into)
    }

    fn class_instance(env: &JNIEnv) -> &'static CacheClassDescr {
        CLASS_DESCR
            .get_or_init(|| try_cache_class_descr(env, Errors::FATAL_TYPE, "(Ljava/lang/String;)V"))
    }
}

///Runtime exception which can be handled on the Java side
pub mod NativeException {
    use super::*;

    static CLASS_DESCR: OnceCell<CacheClassDescr> = OnceCell::new();

    ///Exception code
    #[derive(Debug, Copy, Clone)]
    pub enum Code {
        ContainerRead,
        ContainerOpenUnsupported,
        ContainerOpenMagicIo,
        ContainerOpenUnknownFormat,
        EmptyBook,
        ImageOpen,
        ContainerCantFindFile,
    }

    //names of java's fields
    impl AsRef<str> for Code {
        fn as_ref(&self) -> &str {
            match self {
                Code::ContainerRead => "CODE_CONTAINER_READ",
                Code::ContainerOpenUnsupported => "CODE_CONTAINER_OPEN_UNSUPPORTED",
                Code::ContainerOpenMagicIo => "CODE_CONTAINER_OPEN_MAGIC_IO",
                Code::ContainerOpenUnknownFormat => "CODE_CONTAINER_OPEN_UNKNOWN_FORMAT",
                Code::EmptyBook => "CODE_EMPTY_BOOK",
                Code::ImageOpen => "CODE_IMAGE_OPEN",
                Code::ContainerCantFindFile => "CODE_CONTAINER_CANT_FIND_FILE",
            }
        }
    }

    ///Create a new one exception
    pub fn new<'a>(
        env: &JNIEnv<'a>,
        code: Code,
        msg: impl Into<JNIString>,
    ) -> JniResult<JThrowable<'a>> {
        let msg = env.new_string(msg)?;

        let CacheClassDescr(ref class, ref constructor) = class_instance(env);

        let code = env.get_static_field(class, code, "I").and_then(|v| v.i())?;

        env.new_object_unchecked(
            JClass::from(class.as_obj()),
            *constructor,
            &[code.into(), JObject::from(msg).into()],
        )
        .map(Into::into)
    }

    fn class_instance(env: &JNIEnv) -> &'static CacheClassDescr {
        CLASS_DESCR.get_or_init(|| {
            try_cache_class_descr(env, Errors::EXCEPTION_TYPE, "(ILjava/lang/String;)V")
        })
    }
}
