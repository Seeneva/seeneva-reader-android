use jni::errors::Result as JniResult;
use jni::objects::{JClass, JObject, JValue};
use jni::sys::jboolean;
use jni::JNIEnv;
use once_cell::sync::OnceCell;

use super::{constants::*, try_cache_class_descr, CacheClassDescr};

static CLASS_DESCR: OnceCell<CacheClassDescr> = OnceCell::new();

fn class_init(env: &JNIEnv) -> &'static CacheClassDescr {
    return CLASS_DESCR.get_or_init(|| try_cache_class_descr(env, JAVA_BOOLEAN_TYPE, "(Z)V"));
}

/// Return Java Boolean wrap object or null if [boolean] is [None]
pub fn new<'a>(env: &'a JNIEnv, boolean: Option<jboolean>) -> JniResult<JObject<'a>> {
    match boolean {
        Some(boolean) => {
            let CacheClassDescr(class, constructor) = class_init(env);
            env.new_object_unchecked(
                JClass::from(class.as_obj()),
                *constructor,
                &[JValue::Bool(boolean)],
            )
        }
        None => Ok(JObject::null()),
    }
}
