use super::{constants::*, jclass_to_cache, jmethod_to_cache};

use jni::errors::Result as JniResult;
use jni::objects::{GlobalRef, JClass, JMethodID, JObject, JValue};
use jni::JNIEnv;

#[derive(Clone)]
pub struct ComicPageDataCache<'a> {
    class: GlobalRef,
    constructor: JMethodID<'a>,
}

type ComicPageData = (usize, String);

impl<'a> ComicPageDataCache<'a> {
    pub fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, COMIC_PAGE_DATA_TYPE)?;

        let constructor = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            JNI_CONSTRUCTOR_NAME,
            &format!(
                "({}L{};){}",
                JNI_LONG_LITERAL, JAVA_STRING_TYPE, JNI_VOID_LITERAL
            ),
        )?;

        Ok(ComicPageDataCache { class, constructor })
    }

    ///Map data from Rust implementation to it Java variant
    pub fn new_jobject<'b>(
        &self,
        env: &'b JNIEnv,
        comic_page_data: &ComicPageData,
    ) -> JniResult<JObject<'b>> {
        let (position, file_name) = comic_page_data;

        env.new_object_unchecked(
            JClass::from(self.class.as_obj()),
            self.constructor,
            &[
                JValue::Long(*position as _),
                JObject::from(env.new_string(&file_name)?).into(),
            ],
        )
    }

    ///Create array from provided slice [`comic_pages`]
    pub fn new_array<'b>(
        &self,
        env: &'b JNIEnv,
        comic_pages: &[ComicPageData],
    ) -> JniResult<JObject<'b>> {
        let array = env.new_object_array(
            comic_pages.len() as _,
            JClass::from(self.class.as_obj()),
            JObject::null(),
        )?;

        for (pos, page) in comic_pages.iter().enumerate(){
            let page = self.new_jobject(env, page)?;
            env.set_object_array_element(array, pos as _, page)?;
        }

        Ok(JObject::from(array))
    }
}
