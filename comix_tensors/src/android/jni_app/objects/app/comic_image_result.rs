use jni::errors::Result as JniResult;
use jni::objects::{JClass, JObject};
use jni::sys::jint;
use jni::JNIEnv;
use once_cell::sync::OnceCell;

use super::{constants::*, try_cache_class_descr, CacheClassDescr};

///Convert RGBA [img_buffer] and img dimensions into the Java success object
pub fn new_success<'a>(
    env: &'a JNIEnv,
    img_buffer: &[u8],
    img_w: u32,
    img_h: u32,
) -> JniResult<JObject<'a>> {
    env.new_int_array((img_w * img_h) as _)
        .and_then(|color_array| {
            use itertools::Itertools;

            //rgba byte buffer to int decoded color
            let img_colors = img_buffer
                .chunks_exact(4)
                .map(|color_components| {
                    let r = color_components[0] as jint;
                    let g = color_components[1] as jint;
                    let b = color_components[2] as jint;
                    let a = color_components[3] as jint;

                    a << 24 | (r << 16) | (g << 8) | b
                })
                .collect_vec();

            env.set_int_array_region(color_array, 0, &img_colors)
                .map(|_| color_array)
                .map(JObject::from)
        })
        .and_then(|color_array| success::new(env, color_array, img_w as _, img_h as _))
}

mod success {
    use super::*;

    static CLASS_DESCR: OnceCell<CacheClassDescr> = OnceCell::new();

    pub type ComicsImageSuccess<'a> = JObject<'a>;

    fn class_descr(env: &JNIEnv) -> &'static CacheClassDescr {
        return CLASS_DESCR
            .get_or_init(|| try_cache_class_descr(env, Results::COMICS_IMAGE_TYPE, "([III)V"));
    }

    ///New sucess object from ARGB decoded image colors [img_colors] and image dimensions
    pub fn new<'a>(
        env: &'a JNIEnv,
        img_colors: JObject,
        img_w: jint,
        img_h: jint,
    ) -> JniResult<ComicsImageSuccess<'a>> {
        let CacheClassDescr(class, constructor) = class_descr(env);

        env.new_object_unchecked(
            JClass::from(class.as_obj()),
            *constructor,
            &[img_colors.into(), img_w.into(), img_h.into()],
        )
    }
}
