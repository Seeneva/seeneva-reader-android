use jni::errors::Result as JniResult;
use jni::JNIEnv;
use jni::objects::JObject;
use jni::strings::JNIString;

use super::constants;

use self::class_constructor::{Constructor, JniClassConstructorCache};
pub use self::class_loader::init as init_class_loader;

pub mod app;
mod class_constructor;
mod class_loader;
pub mod java;
mod field_pointer;

///Converts optional string to it Java variant. If [s] is None than [JObject::null] will be returned
fn string_to_jobject<'a, S>(env: &'a JNIEnv, s: Option<&S>) -> JniResult<JObject<'a>>
where
    S: Into<JNIString> + std::convert::AsRef<str>,
{
    Ok(match s {
        Some(s) => env.new_string(s)?.into(),
        None => JObject::null(),
    })
}
