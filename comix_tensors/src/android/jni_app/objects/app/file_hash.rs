use jni::errors::Result as JniResult;
use jni::objects::{JObject, JValue};
use jni::JNIEnv;
use once_cell::sync::Lazy;

use super::{constants, JniClassConstructorCache};

static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
    Lazy::new(|| (constants::FILE_HASH_TYPE, "([BJ)V").into());

pub type FileHash<'a> = JObject<'a>;

///Create new [FileHash] from provided comic book file size [file_size]
/// and hash [file_hash]
pub fn new<'a>(env: &'a JNIEnv, file_size: u64, file_hash: &[u8]) -> JniResult<FileHash<'a>> {
    let file_hash = env.auto_local(env.byte_array_from_slice(file_hash)?);

    CONSTRUCTOR.init(env).and_then(|constructor| {
        constructor.create(&[
            file_hash.as_obj().into(),
            JValue::Long(file_size as _),
        ])
    })
}
