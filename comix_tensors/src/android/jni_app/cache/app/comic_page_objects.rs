use super::{constants::*, jclass_to_cache, jmethod_to_cache};
use crate::ml::ObjectBox;

use jni::errors::Result as JniResult;
use jni::objects::{GlobalRef, JClass, JMethodID, JObject, JValue};
use jni::signature::{JavaType, Primitive};
use jni::sys::{jfloat, jlong};
use jni::JNIEnv;

///Builder to create Java class of comic file detected object
#[derive(Clone)]
pub struct ComicPageObjectsBuilderCache<'a> {
    class: GlobalRef,
    constructor: JMethodID<'a>,
    add_object_method: JMethodID<'a>,
    build_method: JMethodID<'a>,
}

impl ComicPageObjectsBuilderCache<'static> {
    pub fn new(env: &JNIEnv) -> JniResult<Self> {
        let class = jclass_to_cache(env, COMIC_PAGE_OBJECTS_BUILDER_TYPE)?;

        let constructor = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            JNI_CONSTRUCTOR_NAME,
            &format!(
                "({}L{};){}",
                JNI_LONG_LITERAL, JAVA_STRING_TYPE, JNI_VOID_LITERAL
            ),
        )?;

        let add_object_method = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            "addObject",
            &format!(
                "({}{}{}{}{}{}){}",
                JNI_LONG_LITERAL,
                JNI_FLOAT_LITERAL,
                JNI_FLOAT_LITERAL,
                JNI_FLOAT_LITERAL,
                JNI_FLOAT_LITERAL,
                JNI_FLOAT_LITERAL,
                JNI_VOID_LITERAL
            ),
        )?;

        let build_method = jmethod_to_cache(
            env,
            JClass::from(class.as_obj()),
            "build",
            &format!("()L{};", COMIC_PAGE_OBJECTS_TYPE),
        )?;

        Ok(ComicPageObjectsBuilderCache {
            class,
            constructor,
            add_object_method,
            build_method,
        })
    }

    ///Create a new builder using comic page position and name in the source container
    pub fn new_jobject<'a>(
        &self,
        env: &'a JNIEnv,
        page_position: jlong,
        page_name: &str,
    ) -> JniResult<JObject<'a>> {
        env.new_object_unchecked(
            JClass::from(self.class.as_obj()),
            self.constructor,
            &[
                JValue::Long(page_position),
                JObject::from(env.new_string(page_name)?).into(),
            ],
        )
    }

    ///Add object to the [builder]
    pub fn add_object<'a>(
        &self,
        env: &'a JNIEnv,
        builder: JObject,
        object_class_id: jlong,
        object_probability: jfloat,
        object_box: ObjectBox,
    ) -> JniResult<()> {
        let ObjectBox { cx, cy, w, h } = object_box;

        env.call_method_unchecked(
            builder,
            self.add_object_method,
            JavaType::Primitive(Primitive::Void),
            &[
                object_class_id.into(),
                object_probability.into(),
                cx.into(),
                cy.into(),
                w.into(),
                h.into(),
            ],
        )
        .map(|_| ())
    }

    ///Build Java object using provided [builder]
    pub fn build<'a>(&self, env: &'a JNIEnv, builder: JObject) -> JniResult<JObject<'a>> {
        env.call_method_unchecked(
            builder,
            self.build_method,
            JavaType::Object(COMIC_PAGE_OBJECTS_TYPE.to_string()),
            &[],
        )
        .and_then(JValue::l)
    }
}
