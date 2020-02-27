use jni::errors::Result as JniResult;
use jni::objects::{JClass, JObject, JValue};
use jni::JNIEnv;
use once_cell::sync::OnceCell;

use crate::ComicPageMetadata as ComicPageMetadataData;

use super::{constants::*, string_to_jobject, try_cache_class_descr, CacheClassDescr};

static CLASS_DESCR: OnceCell<CacheClassDescr> = OnceCell::new();

pub type ComicPageMetadata<'a> = JObject<'a>;
pub type ComicPageMetadataArray<'a> = JObject<'a>;

fn class_descr(env: &JNIEnv) -> &'static CacheClassDescr {
    return CLASS_DESCR.get_or_init(|| {
        try_cache_class_descr(env, COMIC_BOOK_PAGE_TYPE, "(JLjava/lang/String;II)V")
    });
}

///Map data from Rust implementation to it Java variant
pub fn new<'b>(
    env: &'b JNIEnv,
    comic_page_metadata: &ComicPageMetadataData,
) -> JniResult<ComicPageMetadata<'b>> {
    let CacheClassDescr(class, constructor) = class_descr(env);

    env.new_object_unchecked(
        JClass::from(class.as_obj()),
        *constructor,
        &[
            JValue::Long(comic_page_metadata.pos as _),
            string_to_jobject(env, &Some(comic_page_metadata.name.as_str()))?.into(),
            JValue::Int(comic_page_metadata.width as _),
            JValue::Int(comic_page_metadata.height as _),
        ],
    )
}

///Create array from provided slice [`comic_pages`]
pub fn new_array<'b>(
    env: &'b JNIEnv,
    comic_pages: &[ComicPageMetadataData],
) -> JniResult<ComicPageMetadataArray<'b>> {
    let CacheClassDescr(class, _) = class_descr(env);

    let array = env.new_object_array(
        comic_pages.len() as _,
        JClass::from(class.as_obj()),
        JObject::null(),
    )?;

    for (pos, page_metadata) in comic_pages.iter().enumerate() {
        let page_metadata = new(env, page_metadata)?;
        env.set_object_array_element(array, pos as _, page_metadata)?;
    }

    Ok(JObject::from(array))
}
