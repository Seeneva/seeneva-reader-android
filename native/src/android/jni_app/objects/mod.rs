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

use jni::errors::Result as JniResult;
use jni::JNIEnv;
use jni::objects::JObject;
use jni::strings::JNIString;

use super::constants;

use self::class_constructor::{Constructor, JniClassConstructorCache};
pub use self::class_loader::init as init_class_loader;

pub mod app;
mod class_constructor;
mod class_loader;
pub mod java;
mod field_pointer;

///Converts optional string to it Java variant. If [s] is None than [JObject::null] will be returned
fn string_to_jobject<'a, S>(env: &'a JNIEnv, s: Option<&S>) -> JniResult<JObject<'a>>
where
    S: Into<JNIString> + std::convert::AsRef<str>,
{
    Ok(match s {
        Some(s) => env.new_string(s)?.into(),
        None => JObject::null(),
    })
}
