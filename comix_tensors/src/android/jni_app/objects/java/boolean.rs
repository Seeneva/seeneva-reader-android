use jni::errors::Result as JniResult;
use jni::objects::JObject;
use jni::sys::jboolean;
use jni::JNIEnv;
use once_cell::sync::Lazy;

use super::{constants, new_nullable_obj, JniClassConstructorCache};

static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
    Lazy::new(|| (constants::JAVA_BOOLEAN_TYPE, "(Z)V").into());

pub type Boolean<'a> = JObject<'a>;

/// Return Java Boolean wrap object or null if [boolean] is [None]
pub fn new<'a>(env: &'a JNIEnv, boolean: Option<jboolean>) -> JniResult<Boolean<'a>> {
    CONSTRUCTOR
        .init(env)
        .and_then(|constructor| new_nullable_obj(&constructor, boolean))
}
