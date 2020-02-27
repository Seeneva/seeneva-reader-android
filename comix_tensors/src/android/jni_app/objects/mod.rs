use jni::descriptors::Desc;
use jni::errors::Result as JniResult;
use jni::objects::{GlobalRef, JClass, JMethodID, JObject};
use jni::signature::JavaType;
use jni::strings::JNIString;
use jni::JNIEnv;
use once_cell::sync::OnceCell;

use super::constants;

/// JNI cache
pub mod app;
pub mod java;

static CLASS_LOADER: OnceCell<ClassLoader<'static>> = OnceCell::new();

#[derive(Clone)]
struct ClassLoader<'a> {
    inner: GlobalRef,
    find_class_method: JMethodID<'a>,
}

impl<'a> ClassLoader<'a> {
    fn init(env: &JNIEnv) -> JniResult<Self> {
        env.find_class(constants::COMIC_RACK_METADATA_TYPE)
            .and_then(|o| {
                env.call_method(o.into(), "getClassLoader", "()Ljava/lang/ClassLoader;", &[])
            })
            .and_then(|value| value.l())
            .and_then(|class_loader| env.new_global_ref(class_loader))
            .and_then(|class_loader| {
                env.get_method_id(
                    class_loader.as_obj(),
                    "findClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;",
                )
                .map(|find_class_method| (class_loader, find_class_method))
            })
            .map(|(class_loader, find_class_method)| ClassLoader {
                inner: class_loader,
                find_class_method: find_class_method.into_inner().into(),
            })
    }

    ///Find class by [class_type]
    fn find_class<'b, S: Into<JNIString> + AsRef<str>>(
        &'static self,
        env: &'b JNIEnv,
        class_type: S,
    ) -> JniResult<JClass<'b>> {
        env.call_method_unchecked(
            self.inner.as_obj(),
            self.find_class_method,
            JavaType::Object("Ljava/lang/Class;".to_string()),
            &[JObject::from(env.new_string(class_type)?).into()],
        )
        .and_then(|value| value.l())
        .map(Into::into)
    }
}

unsafe impl Sync for ClassLoader<'static> {}
unsafe impl Send for ClassLoader<'static> {}

type CacheClass = GlobalRef;
type CacheConstructor = JMethodID<'static>;

///Description data to create new instance of the Java object
/// Contains class and constructor references
#[derive(Clone)]
struct CacheClassDescr(CacheClass, CacheConstructor);

unsafe impl Sync for CacheClassDescr {}
unsafe impl Send for CacheClassDescr {}

///Cache ClassLoader to retrieve application Classes
/// ClassLoaded doesn't contain any Java or Android Classes!
pub fn init(env: &JNIEnv) {
    if CLASS_LOADER.get().is_none() {
        info!("Init cache");

        CLASS_LOADER
            .set(ClassLoader::init(env).expect("Init JNI ClassLoader"))
            .ok()
            .expect("Set inited JNI ClassLoader");

        info!("Cache inited");
    } else {
        info!("JNI cache is already init");
    }
}

///Find class by it [class_type]
fn find_class<'a, S: Into<JNIString> + AsRef<str>>(
    env: &'a JNIEnv,
    class_type: S,
) -> JniResult<JClass<'a>> {
    //Should use class loader if class_type located in the app package.
    if class_type.as_ref().starts_with(constants::PACKAGE_NAME) {
        CLASS_LOADER
            .get()
            .expect("ClassLoader is not cached! Init cache first!")
            .find_class(env, class_type)
    } else {
        env.find_class(class_type)
    }
}

///Get description for [class_type]
/// Return tuple of Java class and it constructor
fn try_cache_class_descr(env: &JNIEnv, class_type: &str, init_signature: &str) -> CacheClassDescr {
    class_for_cache(env, class_type)
        .and_then(|class| {
            method_for_cache(
                env,
                JClass::from(class.as_obj()),
                constants::JNI_CONSTRUCTOR_NAME,
                init_signature,
            )
            .map(|constructor| CacheClassDescr(class, constructor))
        })
        .expect(&format!(
            "Can't create cache for {}. Signature {}",
            class_type, init_signature
        ))
}

///Finds Java Class by it name and make it global
fn class_for_cache(env: &JNIEnv, name: &str) -> JniResult<GlobalRef> {
    find_class(env, name).and_then(|class| env.new_global_ref(class.into()))
}

///Finds Java method in the provided class and make it static lifetime
/// Use with classes with static lifetime only!
fn method_for_cache<'a, T>(
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

/////Finds static field in the provided [class] by it [name] and signature [sig]
//fn static_field_for_cache<'a, T>(
//    env: &'a JNIEnv,
//    class: T,
//    name: &str,
//    sig: &str,
//) -> JniResult<JStaticFieldID<'static>>
//where
//    T: Desc<'a, JClass<'a>>,
//{
//    env.get_static_field_id(class, name, format!("L{};", sig))
//        .map(|jmid| jmid.into_inner().into())
//}

///Converts optional string to it Java variant. If [s] is None than [JObject::null] will be returned
fn string_to_jobject<'a, S>(env: &'a JNIEnv, s: &Option<S>) -> JniResult<JObject<'a>>
where
    S: Into<JNIString> + std::convert::AsRef<str>,
{
    Ok(match s {
        Some(s) => env.new_string(s)?.into(),
        None => JObject::null(),
    })
}
