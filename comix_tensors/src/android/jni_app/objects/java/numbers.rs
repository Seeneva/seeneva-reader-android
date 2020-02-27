use jni::errors::Result as JniResult;
use jni::objects::{JClass, JObject, JValue};
use jni::JNIEnv;
use num_traits::Num;
use once_cell::sync::OnceCell;

use super::{constants::*, try_cache_class_descr, CacheClassDescr};

///Base function to create new Java number instance
fn new_number<'a, F, N>(env: &'a JNIEnv, class_init: F, number: Option<N>) -> JniResult<JObject<'a>>
where
    F: FnOnce(&JNIEnv) -> &'static CacheClassDescr,
    N: Into<JValue<'a>> + Num,
{
    match number {
        Some(number) => {
            let CacheClassDescr(class, constructor) = class_init(env);
            env.new_object_unchecked(JClass::from(class.as_obj()), *constructor, &[number.into()])
        }
        None => Ok(JObject::null()),
    }
}

pub mod integer {
    use jni::sys::jint;

    use super::*;

    static CLASS_DESCR: OnceCell<CacheClassDescr> = OnceCell::new();

    pub type Integer<'a> = JObject<'a>;

    fn class_init(env: &JNIEnv) -> &'static CacheClassDescr {
        return CLASS_DESCR.get_or_init(|| try_cache_class_descr(env, JAVA_INTEGER_TYPE, "(I)V"));
    }

    ///Construct new Java Integer object
    pub fn new<'a>(env: &'a JNIEnv, int: Option<jint>) -> JniResult<Integer<'a>> {
        return new_number(env, class_init, int);
    }
}

pub mod long {
    use jni::sys::jlong;

    use super::*;

    static CLASS_DESCR: OnceCell<CacheClassDescr> = OnceCell::new();

    pub type Long<'a> = JObject<'a>;

    fn class_init(env: &JNIEnv) -> &'static CacheClassDescr {
        return CLASS_DESCR.get_or_init(|| try_cache_class_descr(env, JAVA_LONG_TYPE, "(J)V"));
    }

    ///Construct new Java Long object
    pub fn new<'a>(env: &'a JNIEnv, long: Option<jlong>) -> JniResult<Long<'a>> {
        return new_number(env, class_init, long);
    }
}
