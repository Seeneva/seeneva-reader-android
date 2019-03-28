/// JNI cache
mod app;
mod java;

use self::app::*;
use self::java::JavaNumbersCache;
use super::constants;

use std::rc::Rc;
use std::sync::{Arc, RwLock};

use jni::descriptors::Desc;
use jni::errors::Result as JniResult;
use jni::objects::{GlobalRef, JClass, JMethodID, JObject, JStaticFieldID};
use jni::strings::JNIString;
use jni::JNIEnv;

lazy_static! {
    static ref JNI_CACHE: RwLock<Option<Arc<JniCache<'static>>>> = RwLock::new(None);
}

/// Init cache on JNI_OnLoad function
pub fn init_cache(env: &JNIEnv) -> JniResult<()> {
    let inited_result = JNI_CACHE.read().map(|locked_cache| locked_cache.is_some());

    match inited_result {
        Ok(false) => match JNI_CACHE.write() {
            Ok(mut lock) => {
                lock.replace(Arc::new(JniCache::init_cache(&env)?));
            }
            Err(e) => {
                panic!("Can't init jni cache. =>>> {:?}", e);
            }
        },
        Err(e) => {
            panic!("Can't init jni cache. =>>> {:?}", e);
        }
        _ => {}
    }

    Ok(())
}

///Try to get a reference to the current cache.
/// Will Panic if there is no cache or if can't get read lock
pub fn try_get_cache() -> Arc<JniCache<'static>> {
    match JNI_CACHE.try_read() {
        Ok(cache_lock) => Arc::clone(cache_lock.as_ref().expect("")),
        Err(e) => panic!("Can't get jni cache read lock. =>>> {:?}", e),
    }
}

#[derive(Clone)]
pub struct JniCache<'a> {
    pub comic_info: ComicInfoCache<'a>,
    pub comic_page_data: ComicPageDataCache<'a>,
    pub comic_book_open_result: ComicBookOpenResultCache<'a>,
    pub comic_page_object_builder: ComicPageObjectsBuilderCache<'a>,
    pub java_numbers_cache: Rc<JavaNumbersCache<'a>>,
}

unsafe impl Send for JniCache<'static> {}
unsafe impl Sync for JniCache<'static> {}

impl JniCache<'static> {
    fn init_cache(env: &JNIEnv) -> JniResult<Self> {
        let java_numbers_cache = Rc::new(JavaNumbersCache::new(env)?);

        Ok(JniCache {
            comic_info: ComicInfoCache::new(env, Rc::clone(&java_numbers_cache))?,
            comic_page_data: ComicPageDataCache::new(env)?,
            comic_book_open_result: ComicBookOpenResultCache::new(env)?,
            comic_page_object_builder: ComicPageObjectsBuilderCache::new(env)?,
            java_numbers_cache,
        })
    }
}

///Finds Java Class by it name and make it global
fn jclass_to_cache(env: &JNIEnv, name: &str) -> JniResult<GlobalRef> {
    env.find_class(name)
        .and_then(|class| env.new_global_ref(class.into()))
}

///Finds Java methond in the provided class and make it static lifetime
/// Use with classes with static lifetime only!
fn jmethod_to_cache<'a, T>(
    env: &'a JNIEnv,
    class: T,
    name: &str,
    sig: &str,
) -> JniResult<JMethodID<'static>>
where
    T: Desc<'a, JClass<'a>>,
{
    env.get_method_id(class, name, sig)
        .map(|jmid| jmid.into_inner().into())
}

fn jstatic_field_to_cache<'a, T>(
    env: &'a JNIEnv,
    class: T,
    name: &str,
    sig: &str,
) -> JniResult<JStaticFieldID<'static>>
where
    T: Desc<'a, JClass<'a>>,
{
    env.get_static_field_id(class, name, format!("L{};", sig))
        .map(|jmid| jmid.into_inner().into())
}

///Converts optional string to it Java variant. If [s] is None than [JObject::null] will be returned
fn string_to_jobject<'a, S>(env: &'a JNIEnv, s: &Option<S>) -> JniResult<JObject<'a>>
where
    S: Into<JNIString> + std::convert::AsRef<str>,
{
    Ok(match s {
        Some(s) => JObject::from(env.new_string(s)?),
        None => JObject::null(),
    })
}
