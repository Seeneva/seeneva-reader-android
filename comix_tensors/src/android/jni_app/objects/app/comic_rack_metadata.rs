use jni::errors::Result as JniResult;
use jni::objects::{JClass, JObject};
use jni::JNIEnv;
use once_cell::sync::OnceCell;

use crate::{ComicInfo as ComicInfoData, ComicInfoPage as ComicInfoPageData};

use super::{constants::*, java, string_to_jobject, try_cache_class_descr, CacheClassDescr};

static CLASS_DESCR: OnceCell<CacheClassDescr> = OnceCell::new();

pub type ComicRackMetadata<'a> = JObject<'a>;

fn class_descr(env: &JNIEnv) -> &'static CacheClassDescr {
    return CLASS_DESCR.get_or_init(||
        try_cache_class_descr(env,
                              COMIC_RACK_METADATA_TYPE,
                              format!("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Boolean;Ljava/lang/Boolean;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[L{};)V", COMIC_RACK_PAGE_TYPE).as_str()));
}

pub fn new<'a>(env: &'a JNIEnv, comic_info: &Option<ComicInfoData>) -> JniResult<ComicRackMetadata<'a>> {
    let CacheClassDescr(class, constructor) = class_descr(env);

    match comic_info {
        Some(comic_info) => env.new_object_unchecked(
            JClass::from(class.as_obj()),
            *constructor,
            &[
                string_to_jobject(env, &comic_info.title)?.into(),
                string_to_jobject(env, &comic_info.series)?.into(),
                string_to_jobject(env, &comic_info.summary)?.into(),
                java::integer::new(env, comic_info.number.map(|n| n as _))?.into(),
                java::integer::new(env, comic_info.count.map(|n| n as _))?.into(),
                java::integer::new(env, comic_info.volume.map(|n| n as _))?.into(),
                java::integer::new(env, comic_info.page_count.map(|n| n as _))?.into(),
                java::integer::new(env, comic_info.year.map(|n| n as _))?.into(),
                java::integer::new(env, comic_info.month.map(|n| n as _))?.into(),
                java::integer::new(env, comic_info.day.map(|n| n as _))?.into(),
                string_to_jobject(env, &comic_info.publisher)?.into(),
                string_to_jobject(env, &comic_info.writer)?.into(),
                string_to_jobject(env, &comic_info.penciller)?.into(),
                string_to_jobject(env, &comic_info.inker)?.into(),
                string_to_jobject(env, &comic_info.colorist)?.into(),
                string_to_jobject(env, &comic_info.letterer)?.into(),
                string_to_jobject(env, &comic_info.cover_artist)?.into(),
                string_to_jobject(env, &comic_info.editor)?.into(),
                string_to_jobject(env, &comic_info.imprint)?.into(),
                string_to_jobject(env, &comic_info.genre)?.into(),
                string_to_jobject(env, &comic_info.format)?.into(),
                string_to_jobject(env, &comic_info.age_rating)?.into(),
                string_to_jobject(env, &comic_info.teams)?.into(),
                string_to_jobject(env, &comic_info.locations)?.into(),
                string_to_jobject(env, &comic_info.story_arc)?.into(),
                string_to_jobject(env, &comic_info.series_group)?.into(),
                java::boolean::new(env, comic_info.black_and_white.map(|b| b as _))?.into(),
                java::boolean::new(env, comic_info.manga.map(|b| b as _))?.into(),
                string_to_jobject(env, &comic_info.characters)?.into(),
                string_to_jobject(env, &comic_info.web)?.into(),
                string_to_jobject(env, &comic_info.notes)?.into(),
                string_to_jobject(env, &comic_info.language_iso)?.into(),
                page::new_array(env, &comic_info.pages)?.into(),
            ],
        ),
        None => Ok(JObject::null()),
    }
}

pub mod page {
    use super::*;

    static CLASS_DESCR: OnceCell<CacheClassDescr> = OnceCell::new();

    pub type ComicRackPageMetadata<'a> = JObject<'a>;
    pub type ComicRackPageMetadataArray<'a> = JObject<'a>;

    fn class_descr(env: &JNIEnv) -> &'static CacheClassDescr {
        return CLASS_DESCR.get_or_init(||
            try_cache_class_descr(env,
                                  COMIC_RACK_PAGE_TYPE,
                                  "(Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/Long;Ljava/lang/Integer;Ljava/lang/Integer;)V"));
    }

    pub fn new<'a>(env: &'a JNIEnv, page: &ComicInfoPageData) -> JniResult<ComicRackPageMetadata<'a>> {
        let CacheClassDescr(class, constructor) = class_descr(env);

        env.new_object_unchecked(
            JClass::from(class.as_obj()),
            *constructor,
            &[
                java::integer::new(env, page.image.map(|n| n as _))?.into(),
                string_to_jobject(env, &page.image_type)?.into(),
                java::long::new(env, page.image_size.map(|n| n as _))?.into(),
                java::integer::new(env, page.image_width.map(|n| n as _))?.into(),
                java::integer::new(env, page.image_height.map(|n| n as _))?.into(),
            ],
        )
    }

    pub fn new_array<'a>(
        env: &'a JNIEnv,
        pages: &Option<Vec<ComicInfoPageData>>,
    ) -> JniResult<ComicRackPageMetadataArray<'a>> {
        let CacheClassDescr(class, _) = class_descr(env);

        Ok(match pages {
            Some(pages) => {
                let pages_jni_array = env.new_object_array(
                    pages.len() as _,
                    JClass::from(class.as_obj()),
                    JObject::null(),
                )?;

                for (i, page) in pages.into_iter().enumerate() {
                    let page = new(env, page)?;
                    env.set_object_array_element(pages_jni_array, i as _, page)?;
                }

                JObject::from(pages_jni_array)
            }
            None => JObject::null(),
        })
    }
}
