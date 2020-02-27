use jni::errors::Result as JniResult;
use jni::objects::{JClass, JObject};
use jni::JNIEnv;
use once_cell::sync::OnceCell;

use super::{constants::*, try_cache_class_descr, CacheClassDescr};

static CLASS_DESCR: OnceCell<CacheClassDescr> = OnceCell::new();

pub type Task<'a> = JObject<'a>;

fn class_descr(env: &JNIEnv) -> &'static CacheClassDescr {
    return CLASS_DESCR.get_or_init(|| try_cache_class_descr(env, TASK_TYPE, "()V"));
}

pub fn new<'a>(env: &'a JNIEnv) -> JniResult<Task<'a>> {
    let CacheClassDescr(class, constructor) = class_descr(env);

    env.new_object_unchecked(JClass::from(class.as_obj()), *constructor, &[])
}
