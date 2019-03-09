use super::{constants::*, jclass_to_cache, jmethod_to_cache};

use jni::errors::Result as JniResult;
use jni::objects::{GlobalRef, JClass, JMethodID, JObject, JValue};
use jni::sys::{jint, jlong};
use jni::JNIEnv;

use num_traits::Num;

trait JniNumberObjectCache<'a> {
    type JNumberType: Into<JValue<'a>> + Num;

    fn class(&self) -> &GlobalRef;
    fn constructor(&self) -> &JMethodID;

    fn new_jobject<'b>(
        &self,
        env: &'b JNIEnv,
        n: Option<Self::JNumberType>,
    ) -> JniResult<JObject<'b>> {
        Ok(match n {
            Some(n) => env.new_object_unchecked(
                JClass::from(self.class().as_obj()),
                self.constructor().clone(),
                &[n.into()],
            )?,
            None => JObject::null(),
        })
    }
}

#[derive(Clone)]
pub struct JavaNumbersCache<'a> {
    integer: IntegerCache<'a>,
    long: LongCache<'a>,
}

impl JavaNumbersCache<'_> {
    pub fn new(env: &JNIEnv) -> JniResult<Self> {
        Ok(JavaNumbersCache {
            integer: IntegerCache::new(env)?,
            long: LongCache::new(env)?,
        })
    }

    pub fn new_integer<'a>(&self, env: &'a JNIEnv, n: Option<jint>) -> JniResult<JObject<'a>> {
        self.integer.new_jobject(env, n)
    }

    pub fn new_long<'a>(&self, env: &'a JNIEnv, n: Option<jlong>) -> JniResult<JObject<'a>> {
        self.long.new_jobject(env, n)
    }
}

#[derive(Clone)]
struct IntegerCache<'a> {
    class: GlobalRef,
    constructor: JMethodID<'a>,
}

impl IntegerCache<'_> {
    fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, JAVA_INTEGER_TYPE)?;

        let constructor = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            JNI_CONSTRUCTOR_NAME,
            &format!("({}){}", JNI_INT_LITERAL, JNI_VOID_LITERAL),
        )?;

        Ok(IntegerCache { class, constructor })
    }
}

impl JniNumberObjectCache<'_> for IntegerCache<'_> {
    type JNumberType = jint;

    fn class(&self) -> &GlobalRef {
        &self.class
    }

    fn constructor(&self) -> &JMethodID {
        &self.constructor
    }
}

#[derive(Clone)]
struct LongCache<'a> {
    class: GlobalRef,
    constructor: JMethodID<'a>,
}

impl JniNumberObjectCache<'_> for LongCache<'_> {
    type JNumberType = jlong;

    fn class(&self) -> &GlobalRef {
        &self.class
    }

    fn constructor(&self) -> &JMethodID {
        &self.constructor
    }
}

impl LongCache<'_> {
    fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, JAVA_LONG_TYPE)?;

        let constructor = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            JNI_CONSTRUCTOR_NAME,
            &format!("({}){}", JNI_LONG_LITERAL, JNI_VOID_LITERAL),
        )?;

        Ok(LongCache { class, constructor })
    }
}
