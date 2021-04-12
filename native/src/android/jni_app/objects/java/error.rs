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
use jni::objects::JThrowable;
use jni::strings::JNIString;
use jni::JNIEnv;
use once_cell::sync::Lazy;

use super::{constants, JniClassConstructorCache};

pub mod IllegalArgumentException {
    use super::*;

    static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> = Lazy::new(|| {
        (
            constants::Errors::JAVA_ILLEGAL_ARGUMENT_EXCEPTION_TYPE,
            "(Ljava/lang/String;)V",
        )
            .into()
    });

    pub fn new<'a>(env: &'a JNIEnv, msg: impl Into<JNIString>) -> JniResult<JThrowable<'a>> {
        let msg = env.auto_local(env.new_string(msg)?);

        CONSTRUCTOR
            .init(env)
            .and_then(|constructor| constructor.create(&[(&msg).into()]))
            .map(Into::into)
    }
}
