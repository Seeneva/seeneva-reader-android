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

use super::{constants, Constructor, JniClassConstructorCache};

pub use self::numbers::*;

pub mod boolean;
pub mod error;
pub mod numbers;

/// Return Java object or null if [obj] is [None]
fn new_nullable_obj<'jni, O>(
    constructor: &Constructor<'_, 'jni>,
    obj: Option<O>,
) -> JniResult<JObject<'jni>>
where
    O: Into<JValue<'jni>>,
{
    match obj {
        Some(obj) => constructor.create(&[obj.into()]),
        None => Ok(JObject::null()),
    }
}
