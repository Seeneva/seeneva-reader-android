mod cache;
mod constants;

pub use self::cache::*;
use crate::comics::{ComicInfo, ComicPageContent};

use jni::errors::Result as JniResult;
use jni::objects::{GlobalRef, JObject};
use jni::JNIEnv;

mod InnerTrait {
    use jni::objects::JObject;

    ///Trait helper to get jobjects
    pub trait JobjectExt {
        fn get_jobject(&self) -> JObject;
    }
}

pub type ComicBookData = (Vec<(usize, String)>, Option<ComicInfo>);

///Extension helper trait to call all callback methods
pub trait ComicOpenCallback: InnerTrait::JobjectExt {
    fn call_on_comic_opened(
        &self,
        env: &JNIEnv,
        jni_cache: &JniCache,
        comic_book_data: &ComicBookData,
    )-> JniResult<()>  {
        let (comic_pages, comic_info) = comic_book_data;

        //convert pages into array
        let comic_pages = jni_cache.comic_page_data.new_array(env, comic_pages)?;
        let comic_info = jni_cache.comic_info.new_jobject(env, comic_info)?;

        env.call_method(
            self.get_jobject(),
            "onComicBookOpened",
            &format!(
                "([L{};L{};){}",
                constants::COMIC_PAGE_DATA_TYPE,
                constants::COMIC_INFO_TYPE,
                constants::JNI_VOID_LITERAL
            ),
            &[comic_pages.into(), comic_info.into()],
        )?;

        Ok(())
    }
}

impl InnerTrait::JobjectExt for JObject<'_> {
    fn get_jobject(&self) -> JObject {
        *self
    }
}

impl InnerTrait::JobjectExt for GlobalRef {
    fn get_jobject(&self) -> JObject {
        self.as_obj()
    }
}

impl<T> ComicOpenCallback for T where T: InnerTrait::JobjectExt {}
