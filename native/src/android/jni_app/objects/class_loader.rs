/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

use std::fmt::{Debug, Formatter, Result as FmtResult};

use jni::errors::Result as JniResult;
use jni::objects::{GlobalRef, JClass, JMethodID, JObject};
use jni::signature::JavaType;
use jni::strings::JNIString;
use jni::JNIEnv;
use once_cell::sync::OnceCell;

use super::constants;

static CLASS_LOADER: OnceCell<ClassLoader> = OnceCell::new();

/// Represent wrapper around JVM ClassLoader
/// You can use [find_class] to find JVM class from any thread
#[derive(Clone)]
struct ClassLoader {
    inner: GlobalRef,
    find_class_method: JMethodID<'static>,
}

impl ClassLoader {
    pub fn init(env: &JNIEnv) -> JniResult<Self> {
        env.find_class(constants::COMIC_RACK_METADATA_TYPE)
            .and_then(|o| env.call_method(o, "getClassLoader", "()Ljava/lang/ClassLoader;", &[]))
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
    pub fn find_class<'b, S: Into<JNIString>>(
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

impl Debug for ClassLoader {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        f.debug_struct("ClassLoader")
            .field(
                "class_loader_ptr",
                &format_args!("{:p}", self.inner.as_obj().into_inner()),
            )
            .field(
                "get_class_method_ptr",
                &format_args!("{:p}", self.find_class_method.into_inner()),
            )
            .finish()
    }
}

unsafe impl Sync for ClassLoader {}
unsafe impl Send for ClassLoader {}

/// Cache ClassLoader to retrieve application classes from any thread
/// It will panic if ClassLoader can't be initialised
pub fn init(env: &JNIEnv) {
    info!("Init JVM ClassLoader");

    CLASS_LOADER
        .get_or_try_init(|| ClassLoader::init(env))
        .expect("Can't init JVM ClassLoader");
}

///Find class by it [class_type]
pub(super) fn find_class<'a, S: std::convert::AsRef<str> + Into<JNIString>>(
    env: &'a JNIEnv,
    class_type: S,
) -> JniResult<JClass<'a>> {
    //Should use class loader if class_type located in the app package.
    if class_type.as_ref().starts_with(constants::PACKAGE_NAME) {
        CLASS_LOADER
            .get()
            .expect("ClassLoader is not initialised! Init ClassLoader first!")
            .find_class(env, class_type)
    } else {
        env.find_class(class_type)
    }
}
