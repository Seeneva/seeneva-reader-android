use jni::errors::Result as JniResult;
use jni::objects::{JClass, JObject, JString};
use jni::sys::{jbyteArray, jlong};
use jni::JNIEnv;
use once_cell::sync::OnceCell;

use crate::{ComicInfo, ComicPageMetadata};

use super::{
    comic_book_page, comic_rack_metadata, constants::*, try_cache_class_descr, CacheClassDescr,
};

///Init success Java variant
pub fn new_success<'a>(
    env: &'a JNIEnv,
    comics_path: JString,
    comics_name: JString,
    comic_size: u64,
    comic_hash: &[u8],
    comic_cover_position: usize,
    pages_metadata: &[ComicPageMetadata],
    comic_info: &Option<ComicInfo>,
) -> JniResult<JObject<'a>> {
    let comic_hash = env.byte_array_from_slice(comic_hash)?;

    success::new(
        env,
        comics_path,
        comics_name,
        comic_size as _,
        comic_hash,
        comic_cover_position as _,
        pages_metadata,
        comic_info,
    )
}

mod success {
    use super::*;

    static CLASS_DESCR: OnceCell<CacheClassDescr> = OnceCell::new();

    pub type ComicBookSuccess<'a> = JObject<'a>;

    fn class_descr(env: &JNIEnv) -> &'static CacheClassDescr {
        return CLASS_DESCR.get_or_init(|| {
            try_cache_class_descr(
                env,
                Results::COMIC_BOOK_TYPE,
                format!(
                    "(Ljava/lang/String;J[BLjava/lang/String;JL{};[L{};)V",
                    COMIC_RACK_METADATA_TYPE, COMIC_BOOK_PAGE_TYPE
                )
                .as_str(),
            )
        });
    }

    ///Init success Java variant
    pub fn new<'a>(
        env: &'a JNIEnv,
        comic_path: JString,
        comic_name: JString,
        comic_size: jlong,
        comic_hash: jbyteArray,
        comic_cover_position: jlong,
        pages_metadata: &[ComicPageMetadata],
        comic_info: &Option<ComicInfo>,
    ) -> JniResult<ComicBookSuccess<'a>> {
        let comic_hash = JObject::from(comic_hash);

        let CacheClassDescr(class, constructor) = class_descr(env);

        let comic_title = create_comic_title(env, comic_info, comic_name)?;
        let comic_rack_metadata = comic_rack_metadata::new(env, comic_info)?;
        let comic_book_pages = comic_book_page::new_array(env, pages_metadata)?;

        env.new_object_unchecked(
            JClass::from(class.as_obj()),
            *constructor,
            &[
                JObject::from(comic_path).into(),
                comic_size.into(),
                comic_hash.into(),
                JObject::from(comic_title).into(),
                comic_cover_position.into(),
                comic_rack_metadata.into(),
                comic_book_pages.into(),
            ],
        )
    }

    ///Create comic book title fom [ComicInfo] if provided. Or return [default_title]
    fn create_comic_title<'a>(
        env: &'a JNIEnv,
        comic_info: &Option<ComicInfo>,
        default_title: JString<'a>,
    ) -> JniResult<JString<'a>> {
        comic_info
            .as_ref()
            .and_then(|comic_info| match &comic_info.series {
                Some(title) => Some(match &comic_info.number {
                    Some(number) => format!("{} #{}", title, number),
                    None => title.to_owned(),
                }),
                None => None,
            })
            .map_or(Ok(default_title), |title| env.new_string(title))
    }
}
