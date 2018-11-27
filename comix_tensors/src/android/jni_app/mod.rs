mod cache;
mod constants;

pub use self::cache::*;
use crate::comics::ComicInfo;

use jni::errors::Result as JniResult;
use jni::objects::{GlobalRef, JByteBuffer, JObject, JValue};
use jni::JNIEnv;

mod InnerTrait {
    use jni::objects::JObject;

    ///Trait helper to get jobjects
    pub trait JobjectExt {
        fn get_jobject(&self) -> JObject;
    }
}

///Extension helper trait to call all callback methods
pub trait ComicsProcessingCallback: InnerTrait::JobjectExt {
    ///Send [comic_info] to the Android App via JNI
    fn call_on_comic_info_parsed(
        &self,
        env: &JNIEnv,
        jni_cache: &JniCache,
        comic_info: &ComicInfo,
    ) -> JniResult<()> {
        jni_cache
            .comic_info
            .new_jobject(&env, &comic_info)
            .and_then(|comic_info| {
                env.call_method(
                    self.get_jobject(),
                    "onComicInfoParsed",
                    &format!(
                        "(L{};){}",
                        constants::COMIC_INFO_TYPE,
                        constants::JNI_VOID_LITERAL
                    ),
                    &[comic_info.into()],
                )
            })
            .map(|_| ())
    }

    ///Send a batch of prepared to TF interpreter byte buffers. [interpreter_batched_input] is prepared images.
    /// Output is a byte buffer of floats
    /// Will panic if output is not a Jobject
    fn call_on_pages_batch_prepared<'a>(
        &self,
        env: &'a JNIEnv,
        interpreter_batched_input: JByteBuffer,
    ) -> JniResult<JByteBuffer<'a>> {
        env.call_method(
            self.get_jobject(),
            "onPagesBatchPrepared",
            format!(
                "(L{};)L{};",
                constants::JAVA_BYTE_BUFFER_TYPE,
                constants::JAVA_BYTE_BUFFER_TYPE
            ),
            &[JValue::Object(interpreter_batched_input.into())],
        )
        .map(|value| {
            value
                .l()
                .expect("The response from TF interpreter should be a ByteBuffer of floats")
        })
        .map(|object| JByteBuffer::from(object))
    }

    ///Send to the Android app all detected objects [comic_page_objects] at the comic page
    fn call_on_comic_page_objects_detected(
        &self,
        env: &JNIEnv,
        comic_page_objects: JObject,
    ) -> JniResult<()> {
        env.call_method(
            self.get_jobject(),
            "onComicPageObjectsDetected",
            format!(
                "(L{};){}",
                constants::COMIC_PAGE_OBJECTS_TYPE,
                constants::JNI_VOID_LITERAL
            ),
            &[comic_page_objects.into()],
        )
        .map(|_| ())
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

impl<T> ComicsProcessingCallback for T where T: InnerTrait::JobjectExt {}
