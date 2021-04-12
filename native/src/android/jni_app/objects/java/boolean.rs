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
use jni::objects::JObject;
use jni::sys::jboolean;
use jni::JNIEnv;
use once_cell::sync::Lazy;

use super::{constants, new_nullable_obj, JniClassConstructorCache};

static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
    Lazy::new(|| (constants::JAVA_BOOLEAN_TYPE, "(Z)V").into());

pub type Boolean<'a> = JObject<'a>;

/// Return Java Boolean wrap object or null if [boolean] is [None]
pub fn new<'a>(env: &'a JNIEnv, boolean: Option<jboolean>) -> JniResult<Boolean<'a>> {
    CONSTRUCTOR
        .init(env)
        .and_then(|constructor| new_nullable_obj(&constructor, boolean))
}
