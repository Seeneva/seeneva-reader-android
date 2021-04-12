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
use jni::objects::{JObject, JValue};
use jni::JNIEnv;
use once_cell::sync::Lazy;

use super::{constants, JniClassConstructorCache};

static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
    Lazy::new(|| (constants::FILE_HASH_TYPE, "([BJ)V").into());

pub type FileHash<'a> = JObject<'a>;

///Create new [FileHash] from provided comic book file size [file_size]
/// and hash [file_hash]
pub fn new<'a>(env: &'a JNIEnv, file_size: u64, file_hash: &[u8]) -> JniResult<FileHash<'a>> {
    let file_hash = env.auto_local(env.byte_array_from_slice(file_hash)?);

    CONSTRUCTOR.init(env).and_then(|constructor| {
        constructor.create(&[
            file_hash.as_obj().into(),
            JValue::Long(file_size as _),
        ])
    })
}
