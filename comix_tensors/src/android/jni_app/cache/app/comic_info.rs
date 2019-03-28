use super::{constants::*, jclass_to_cache, jmethod_to_cache, string_to_jobject, JavaNumbersCache};

use crate::{ComicInfo as ComicInfoData, ComicInfoPage as ComicInfoPageData};

use std::rc::Rc;

use jni::errors::Result as JniResult;
use jni::objects::{GlobalRef, JClass, JMethodID, JObject};
use jni::sys::jsize;
use jni::JNIEnv;

#[derive(Clone)]
pub struct ComicInfoCache<'a> {
    info: ComicInfo<'a>,
    page: ComicInfoPage<'a>,
    number_cache: Rc<JavaNumbersCache<'a>>,
}

impl<'a> ComicInfoCache<'a> {
    pub fn new(env: &JNIEnv, number_cache: Rc<JavaNumbersCache<'a>>) -> JniResult<Self> {
        Ok(ComicInfoCache {
            info: ComicInfo::new(env)?,
            page: ComicInfoPage::new(env)?,
            number_cache,
        })
    }

    ///Map data from Rust [ComicInfoData] implementation to it Java variant
    pub fn new_jobject<'b>(
        &self,
        env: &'b JNIEnv,
        comic_info: &Option<ComicInfoData>,
    ) -> JniResult<JObject<'b>> {
        match comic_info {
            Some(comic_info) => self
                .info
                .init(env, &self.page, &self.number_cache, comic_info),
            None => Ok(JObject::null()),
        }
    }
}

#[derive(Clone)]
struct ComicInfo<'a> {
    class: GlobalRef,
    constructor: JMethodID<'a>,
}

impl ComicInfo<'_> {
    fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, COMIC_INFO_TYPE)?;
        let constructor = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            JNI_CONSTRUCTOR_NAME,
            &format!(
                "(\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 {}\
                 {}\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 [L{};\
                 )\
                 {}",
                JAVA_STRING_TYPE,
                JAVA_STRING_TYPE,
                JAVA_STRING_TYPE,
                JAVA_INTEGER_TYPE,
                JAVA_INTEGER_TYPE,
                JAVA_INTEGER_TYPE,
                JAVA_INTEGER_TYPE,
                JAVA_STRING_TYPE,
                JAVA_STRING_TYPE,
                JAVA_STRING_TYPE,
                JAVA_STRING_TYPE,
                JAVA_STRING_TYPE,
                JNI_BOOLEAN_LITERAL,
                JNI_BOOLEAN_LITERAL,
                JAVA_STRING_TYPE,
                JAVA_INTEGER_TYPE,
                JAVA_STRING_TYPE,
                JAVA_STRING_TYPE,
                JAVA_STRING_TYPE,
                JAVA_STRING_TYPE,
                COMIC_INFO_PAGE_TYPE,
                JNI_VOID_LITERAL
            ),
        )?;

        Ok(ComicInfo { class, constructor })
    }

    fn init<'a>(
        &self,
        env: &'a JNIEnv,
        page_cache: &ComicInfoPage,
        number_cache: &JavaNumbersCache,
        comic_info: &ComicInfoData,
    ) -> JniResult<JObject<'a>> {
        let pages_array = page_cache.new_jarray(env, number_cache, &comic_info.pages)?;

        env.new_object_unchecked(
            JClass::from(self.class.as_obj()),
            self.constructor,
            &[
                string_to_jobject(env, &comic_info.title)?.into(),
                string_to_jobject(env, &comic_info.series)?.into(),
                string_to_jobject(env, &comic_info.summary)?.into(),
                number_cache
                    .new_integer(&env, comic_info.number.map(|n| n as _))?
                    .into(),
                number_cache
                    .new_integer(&env, comic_info.count.map(|n| n as _))?
                    .into(),
                number_cache
                    .new_integer(&env, comic_info.year.map(|n| n as _))?
                    .into(),
                number_cache
                    .new_integer(&env, comic_info.month.map(|n| n as _))?
                    .into(),
                string_to_jobject(env, &comic_info.writer)?.into(),
                string_to_jobject(env, &comic_info.publisher)?.into(),
                string_to_jobject(env, &comic_info.penciller)?.into(),
                string_to_jobject(env, &comic_info.cover_artist)?.into(),
                string_to_jobject(env, &comic_info.genre)?.into(),
                comic_info.black_and_white.into(),
                comic_info.manga.into(),
                string_to_jobject(env, &comic_info.characters)?.into(),
                number_cache
                    .new_integer(&env, comic_info.page_count.map(|n| n as _))?
                    .into(),
                string_to_jobject(env, &comic_info.web)?.into(),
                string_to_jobject(env, &comic_info.notes)?.into(),
                string_to_jobject(env, &comic_info.volume)?.into(),
                string_to_jobject(env, &comic_info.language_iso)?.into(),
                pages_array.into(),
            ],
        )
    }
}

#[derive(Clone)]
struct ComicInfoPage<'a> {
    class: GlobalRef,
    constructor: JMethodID<'a>,
}

impl ComicInfoPage<'_> {
    fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, COMIC_INFO_PAGE_TYPE)?;
        let constructor = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            JNI_CONSTRUCTOR_NAME,
            &format!(
                "(\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 L{};\
                 )\
                 {}",
                JAVA_INTEGER_TYPE,
                JAVA_STRING_TYPE,
                JAVA_LONG_TYPE,
                JAVA_INTEGER_TYPE,
                JAVA_INTEGER_TYPE,
                JNI_VOID_LITERAL
            ),
        )?;

        Ok(ComicInfoPage { class, constructor })
    }

    fn new_jarray<'a>(
        &self,
        env: &'a JNIEnv,
        number_cache: &JavaNumbersCache,
        pages: &Option<Vec<ComicInfoPageData>>,
    ) -> JniResult<JObject<'a>> {
        Ok(match pages {
            Some(pages) => {
                let pages_jni_array = env.new_object_array(
                    pages.len() as jsize,
                    JClass::from(self.class.as_obj()),
                    JObject::null(),
                )?;

                for (i, page) in pages.into_iter().enumerate() {
                    let page = self.new_jobject(env, number_cache, page)?;
                    env.set_object_array_element(pages_jni_array, i as jsize, page)?;
                }

                JObject::from(pages_jni_array)
            }
            None => JObject::null(),
        })
    }

    fn new_jobject<'a>(
        &self,
        env: &'a JNIEnv,
        number_cache: &JavaNumbersCache,
        page: &ComicInfoPageData,
    ) -> JniResult<JObject<'a>> {
        env.new_object_unchecked(
            JClass::from(self.class.as_obj()),
            self.constructor,
            &[
                number_cache
                    .new_integer(env, page.image.map(|n| n as _))?
                    .into(),
                string_to_jobject(env, &page.image_type)?.into(),
                number_cache
                    .new_long(env, page.image_size.map(|n| n as _))?
                    .into(),
                number_cache
                    .new_integer(env, page.image_width.map(|n| n as _))?
                    .into(),
                number_cache
                    .new_integer(env, page.image_height.map(|n| n as _))?
                    .into(),
            ],
        )
    }
}
