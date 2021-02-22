use std::convert::TryInto;

use jni::errors::Result as JniResult;
use jni::objects::{JObject, JValue};
use jni::JNIEnv;
use once_cell::sync::Lazy;

use crate::comics::ml::BoundingBox;

use super::{constants, JniClassConstructorCache};

static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
    Lazy::new(|| (constants::COMIC_BOOK_PAGE_OBJECT_TYPE, "(JFFFFF)V").into());

pub type PageObject<'jni> = JObject<'jni>;
pub type PageObjects<'jni> = JObject<'jni>;

/// Create new JNI PageObject
pub fn new<'jni>(env: &'jni JNIEnv, b_box: &BoundingBox) -> JniResult<PageObject<'jni>> {
    CONSTRUCTOR.init(env).and_then(|constructor| {
        constructor.create(&[
            JValue::Long(
                (b_box.class as u32)
                    .try_into()
                    .expect("Can't convert BBox class into Int"),
            ),
            JValue::Float(
                b_box
                    .prob
                    .try_into()
                    .expect("Can't convert BBox probability into Float"),
            ),
            JValue::Float(
                b_box
                    .y_min
                    .try_into()
                    .expect("Can't convert BBox y_min into Float"),
            ),
            JValue::Float(
                b_box
                    .x_min
                    .try_into()
                    .expect("Can't convert BBox x_min into Float"),
            ),
            JValue::Float(
                b_box
                    .y_max
                    .try_into()
                    .expect("Can't convert BBox y_max into Float"),
            ),
            JValue::Float(
                b_box
                    .x_max
                    .try_into()
                    .expect("Can't convert BBox x_max into Float"),
            ),
        ])
    })
}

/// Create new JNI array of PageObject's
pub fn new_array<'jni>(env: &'jni JNIEnv, b_boxes: &[BoundingBox]) -> JniResult<PageObjects<'jni>> {
    env.with_local_frame(b_boxes.len() as _, || {
        let b_boxes_array = env.new_object_array(
            b_boxes.len() as _,
            CONSTRUCTOR.init(env)?.class_obj(),
            JObject::null(),
        )?;

        for (pos, b_box) in b_boxes.iter().enumerate() {
            env.set_object_array_element(b_boxes_array, pos as _, new(env, b_box)?)?;
        }

        Ok(JObject::from(b_boxes_array))
    })
}
