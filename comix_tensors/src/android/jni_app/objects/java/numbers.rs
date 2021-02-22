use jni::errors::Result as JniResult;
use jni::objects::{JObject, JValue};
use jni::JNIEnv;
use num_traits::Num;
use once_cell::sync::Lazy;

use super::{constants, new_nullable_obj, Constructor, JniClassConstructorCache};

///Base function to create new Java number instances
fn new_number<'jni, N>(
    constructor: &Constructor<'_, 'jni>,
    number: Option<N>,
) -> JniResult<JObject<'jni>>
where
    N: Into<JValue<'jni>> + Num,
{
    new_nullable_obj(constructor, number)
}

pub mod integer {
    use jni::sys::jint;

    use super::*;

    static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
        Lazy::new(|| (constants::JAVA_INTEGER_TYPE, "(I)V").into());

    pub type Integer<'a> = JObject<'a>;

    ///Construct new Java Integer object
    pub fn new<'a>(env: &'a JNIEnv, int: Option<jint>) -> JniResult<Integer<'a>> {
        new_number(&CONSTRUCTOR.init(env)?, int)
    }
}

pub mod long {
    use jni::sys::jlong;

    use super::*;

    static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
        Lazy::new(|| (constants::JAVA_LONG_TYPE, "(J)V").into());

    pub type Long<'a> = JObject<'a>;

    ///Construct new Java Long object
    pub fn new<'a>(env: &'a JNIEnv, long: Option<jlong>) -> JniResult<Long<'a>> {
        new_number(&CONSTRUCTOR.init(env)?, long)
    }
}
