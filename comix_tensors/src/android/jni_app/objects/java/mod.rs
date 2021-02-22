use jni::errors::Result as JniResult;
use jni::objects::{JObject, JValue};

use super::{constants, Constructor, JniClassConstructorCache};

pub use self::numbers::*;

pub mod boolean;
pub mod error;
pub mod numbers;

/// Return Java object or null if [obj] is [None]
fn new_nullable_obj<'jni, O>(
    constructor: &Constructor<'_, 'jni>,
    obj: Option<O>,
) -> JniResult<JObject<'jni>>
where
    O: Into<JValue<'jni>>,
{
    match obj {
        Some(obj) => constructor.create(&[obj.into()]),
        None => Ok(JObject::null()),
    }
}
