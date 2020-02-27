use jni::errors::Result as JniResult;
use jni::objects::{JClass, JObject, JValue};
use jni::JNIEnv;
use once_cell::sync::OnceCell;

use super::{constants::*, try_cache_class_descr, CacheClassDescr};

static CLASS_DESCR: OnceCell<CacheClassDescr> = OnceCell::new();

pub type FileHash<'a> = JObject<'a>;

fn class_descr(env: &JNIEnv) -> &'static CacheClassDescr {
    return CLASS_DESCR.get_or_init(|| try_cache_class_descr(env, FILE_HASH_TYPE, "([BJ)V"));
}

///Create new [FileHash] from provided comic book file size [file_size]
/// and hash [file_hash]
pub fn new<'a>(
    env: &'a JNIEnv,
    file_size: u64,
    file_hash: &(impl AsRef<[u8]>),
) -> JniResult<FileHash<'a>> {
    let CacheClassDescr(class, constructor) = class_descr(env);

    let file_hash = env.byte_array_from_slice(file_hash.as_ref())?;

    env.new_object_unchecked(
        JClass::from(class.as_obj()),
        *constructor,
        &[
            JObject::from(file_hash).into(),
            JValue::Long(file_size as _),
        ],
    )
}
