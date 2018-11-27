use super::{constants::*, jclass_to_cache, jmethod_to_cache, jstatic_field_to_cache};
use crate::android::tasks::PreprocessComicFilesError;

use jni::errors::Result as JniResult;
use jni::objects::{GlobalRef, JClass, JMethodID, JObject, JStaticFieldID, JValue};
use jni::signature::JavaType;
use jni::JNIEnv;

///JNI cache of related to [PreprocessComicFilesError] java classes
#[derive(Clone)]
pub struct ProcessComicFilesResultCache<'a> {
    success: Success<'a>,
    cancelled: Cancelled<'a>,
    container_read_error: ContainerReadError<'a>,
    container_open_error: ContainerOpenError<'a>,
    container_open_error_kind: ContainerOpenErrorKind<'a>,
    jni_error: JniError<'a>,
    cancellation_error: CancellationError<'a>,
    preprocessing_error: PreprocessingError<'a>,
}

impl ProcessComicFilesResultCache<'_> {
    pub fn new(env: &JNIEnv) -> JniResult<Self> {
        Ok(ProcessComicFilesResultCache {
            success: Success::new(env)?,
            cancelled: Cancelled::new(env)?,
            container_read_error: ContainerReadError::new(env)?,
            container_open_error: ContainerOpenError::new(env)?,
            container_open_error_kind: ContainerOpenErrorKind::new(env)?,
            jni_error: JniError::new(env)?,
            cancellation_error: CancellationError::new(env)?,
            preprocessing_error: PreprocessingError::new(env)?,
        })
    }

    ///Init success Java variant
    pub fn new_success<'a>(&self, env: &'a JNIEnv) -> JniResult<JObject<'a>> {
        self.success.new_jobject(env)
    }

    ///Map error to it Java variant
    pub fn new_error<'a>(
        &self,
        env: &'a JNIEnv,
        err: &PreprocessComicFilesError,
    ) -> JniResult<JObject<'a>> {
        use self::PreprocessComicFilesError::*;

        match err {
            Jni(_) => self.jni_error.new_jobject(env, err),
            ContainerRead(_) => self.container_read_error.new_jobject(env, err),
            ContainerOpen(inner) => self.container_open_error.new_jobject(
                env,
                &self.container_open_error_kind,
                err,
                inner,
            ),
            Preprocess(_) => self.preprocessing_error.new_jobject(env, err),
            CancelledError(_) => self.cancellation_error.new_jobject(env, err),
            Cancelled => self.cancelled.new_jobject(env),
        }
    }
}

#[derive(Clone)]
pub struct Success<'a> {
    class: GlobalRef,
    constructor: JMethodID<'a>,
}

impl Success<'_> {
    fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, STATE_SUCCESS_TYPE)?;

        let constructor = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            JNI_CONSTRUCTOR_NAME,
            &format!("(){}", JNI_VOID_LITERAL),
        )?;

        Ok(Success { class, constructor })
    }

    fn new_jobject<'a>(&self, env: &'a JNIEnv) -> JniResult<JObject<'a>> {
        env.new_object_unchecked(JClass::from(self.class.as_obj()), self.constructor, &[])
    }
}

#[derive(Clone)]
pub struct Cancelled<'a> {
    class: GlobalRef,
    constructor: JMethodID<'a>,
}

impl Cancelled<'_> {
    fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, STATE_CANCELLED_TYPE)?;

        let constructor = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            JNI_CONSTRUCTOR_NAME,
            &format!("(){}", JNI_VOID_LITERAL),
        )?;

        Ok(Cancelled { class, constructor })
    }

    fn new_jobject<'a>(&self, env: &'a JNIEnv) -> JniResult<JObject<'a>> {
        env.new_object_unchecked(JClass::from(self.class.as_obj()), self.constructor, &[])
    }
}

#[derive(Clone)]
pub struct ContainerReadError<'a> {
    class: GlobalRef,
    constructor: JMethodID<'a>,
}

impl ContainerReadError<'_> {
    fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, STATE_CONTAINER_READ_ERROR_TYPE)?;

        let constructor = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            JNI_CONSTRUCTOR_NAME,
            &format!("(L{};){}", JAVA_STRING_TYPE, JNI_VOID_LITERAL),
        )?;

        Ok(ContainerReadError { class, constructor })
    }

    fn new_jobject<'a>(
        &self,
        env: &'a JNIEnv,
        err: &PreprocessComicFilesError,
    ) -> JniResult<JObject<'a>> {
        env.new_object_unchecked(
            JClass::from(self.class.as_obj()),
            self.constructor,
            &[JObject::from(env.new_string(err.to_string())?).into()],
        )
    }
}

#[derive(Clone)]
pub struct ContainerOpenError<'a> {
    class: GlobalRef,
    constructor: JMethodID<'a>,
}

impl ContainerOpenError<'_> {
    fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, STATE_CONTAINER_OPEN_ERROR_TYPE)?;

        let constructor = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            JNI_CONSTRUCTOR_NAME,
            &format!(
                "(L{};L{};){}",
                STATE_CONTAINER_OPEN_ERROR_KIND_TYPE, JAVA_STRING_TYPE, JNI_VOID_LITERAL
            ),
        )?;

        Ok(ContainerOpenError { class, constructor })
    }

    fn new_jobject<'a>(
        &self,
        env: &'a JNIEnv,
        kind_cache: &ContainerOpenErrorKind,
        err: &PreprocessComicFilesError,
        inner_err: &crate::ComicContainerError,
    ) -> JniResult<JObject<'a>> {
        use crate::ComicContainerError::*;

        let kind = match inner_err {
            UnsupportedType(_) => kind_cache.get_unsupported_type_kind(env),
            Magic(magic_err) => {
                use crate::MagicTypeError::*;
                match magic_err {
                    IO(_) => kind_cache.get_magic_io_kind(env),
                    NotFile => kind_cache.get_not_file_kind(env),
                    UnknownFormat => kind_cache.get_unknown_file_format_kind(env),
                }
            }
        }?;

        env.new_object_unchecked(
            JClass::from(self.class.as_obj()),
            self.constructor,
            &[kind, JObject::from(env.new_string(err.to_string())?).into()],
        )
    }
}

#[derive(Clone)]
pub struct ContainerOpenErrorKind<'a> {
    class: GlobalRef,
    unsupported_type_field: JStaticFieldID<'a>,
    magic_io_field: JStaticFieldID<'a>,
    not_file_field: JStaticFieldID<'a>,
    unknown_file_format_field: JStaticFieldID<'a>,
}

impl ContainerOpenErrorKind<'_> {
    fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, STATE_CONTAINER_OPEN_ERROR_KIND_TYPE)?;

        let unsupported_type_field = jstatic_field_to_cache(
            env,
            JClass::from(class.as_obj()),
            "UnsupportedType",
            STATE_CONTAINER_OPEN_ERROR_KIND_TYPE,
        )?;

        let magic_io_field = jstatic_field_to_cache(
            env,
            JClass::from(class.as_obj()),
            "MagicIO",
            STATE_CONTAINER_OPEN_ERROR_KIND_TYPE,
        )?;

        let not_file_field = jstatic_field_to_cache(
            env,
            JClass::from(class.as_obj()),
            "NotFile",
            STATE_CONTAINER_OPEN_ERROR_KIND_TYPE,
        )?;

        let unknown_file_format_field = jstatic_field_to_cache(
            env,
            JClass::from(class.as_obj()),
            "UnknownFileFormat",
            STATE_CONTAINER_OPEN_ERROR_KIND_TYPE,
        )?;

        Ok(ContainerOpenErrorKind {
            class,
            unsupported_type_field,
            magic_io_field,
            not_file_field,
            unknown_file_format_field,
        })
    }

    fn get_unsupported_type_kind<'a>(&'a self, env: &'a JNIEnv) -> JniResult<JValue<'a>> {
        self.get_kind(env, self.unsupported_type_field)
    }

    fn get_magic_io_kind<'a>(&'a self, env: &'a JNIEnv) -> JniResult<JValue<'a>> {
        self.get_kind(env, self.magic_io_field)
    }

    fn get_not_file_kind<'a>(&'a self, env: &'a JNIEnv) -> JniResult<JValue<'a>> {
        self.get_kind(env, self.not_file_field)
    }

    fn get_unknown_file_format_kind<'a>(&'a self, env: &'a JNIEnv) -> JniResult<JValue<'a>> {
        self.get_kind(env, self.unknown_file_format_field)
    }

    fn get_kind<'a>(
        &'a self,
        env: &'a JNIEnv,
        filed_id: JStaticFieldID<'a>,
    ) -> JniResult<JValue<'a>> {
        env.get_static_field_unchecked(
            JClass::from(self.class.as_obj()),
            filed_id,
            JavaType::Object(STATE_CONTAINER_OPEN_ERROR_KIND_TYPE.into()),
        )
    }
}

#[derive(Clone)]
pub struct JniError<'a> {
    class: GlobalRef,
    constructor: JMethodID<'a>,
}

impl JniError<'_> {
    fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, STATE_JNI_ERROR_TYPE)?;

        let constructor = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            JNI_CONSTRUCTOR_NAME,
            &format!("(L{};){}", JAVA_STRING_TYPE, JNI_VOID_LITERAL),
        )?;

        Ok(JniError { class, constructor })
    }

    fn new_jobject<'a>(
        &self,
        env: &'a JNIEnv,
        err: &PreprocessComicFilesError,
    ) -> JniResult<JObject<'a>> {
        env.new_object_unchecked(
            JClass::from(self.class.as_obj()),
            self.constructor,
            &[JObject::from(env.new_string(err.to_string())?).into()],
        )
    }
}

#[derive(Clone)]
pub struct CancellationError<'a> {
    class: GlobalRef,
    constructor: JMethodID<'a>,
}

impl CancellationError<'_> {
    fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, STATE_CANCELLATION_ERROR_TYPE)?;

        let constructor = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            JNI_CONSTRUCTOR_NAME,
            &format!("(L{};){}", JAVA_STRING_TYPE, JNI_VOID_LITERAL),
        )?;

        Ok(CancellationError { class, constructor })
    }

    fn new_jobject<'a>(
        &self,
        env: &'a JNIEnv,
        err: &PreprocessComicFilesError,
    ) -> JniResult<JObject<'a>> {
        env.new_object_unchecked(
            JClass::from(self.class.as_obj()),
            self.constructor,
            &[JObject::from(env.new_string(err.to_string())?).into()],
        )
    }
}

#[derive(Clone)]
pub struct PreprocessingError<'a> {
    class: GlobalRef,
    constructor: JMethodID<'a>,
}

impl PreprocessingError<'_> {
    fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, STATE_PREPROCESSING_ERROR_TYPE)?;

        let constructor = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            JNI_CONSTRUCTOR_NAME,
            &format!("(L{};){}", JAVA_STRING_TYPE, JNI_VOID_LITERAL),
        )?;

        Ok(PreprocessingError { class, constructor })
    }

    fn new_jobject<'a>(
        &self,
        env: &'a JNIEnv,
        err: &PreprocessComicFilesError,
    ) -> JniResult<JObject<'a>> {
        env.new_object_unchecked(
            JClass::from(self.class.as_obj()),
            self.constructor,
            &[JObject::from(env.new_string(err.to_string())?).into()],
        )
    }
}
