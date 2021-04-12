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
use jni::objects::JThrowable;
use jni::strings::JNIString;
use once_cell::sync::Lazy;

use super::{constants, JniClassConstructorCache};

///Represent any fatal error
pub mod NativeFatalError {
    use super::*;

    static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
        Lazy::new(|| (constants::Errors::FATAL_TYPE, "(Ljava/lang/String;)V").into());

    ///Create a new one error
    pub fn new<'a>(env: &'a JNIEnv, msg: impl Into<JNIString>) -> JniResult<JThrowable<'a>> {
        let msg = env.auto_local(env.new_string(msg)?);

        CONSTRUCTOR
            .init(env)
            .and_then(|constructor| constructor.create(&[msg.as_obj().into()]))
            .map(Into::into)
    }
}

///Runtime exception which can be handled on the Java side
pub mod NativeException {
    use super::*;

    static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
        Lazy::new(|| (constants::Errors::EXCEPTION_TYPE, "(ILjava/lang/String;)V").into());

    ///Exception code
    #[derive(Debug, Copy, Clone)]
    pub enum Code {
        ContainerRead,
        ContainerOpenUnsupported,
        EmptyBook,
        ImageOpen,
        ContainerCantFindFile,
    }

    //names of java's fields
    impl AsRef<str> for Code {
        fn as_ref(&self) -> &str {
            match self {
                Code::ContainerRead => "CODE_CONTAINER_READ",
                Code::ContainerOpenUnsupported => "CODE_CONTAINER_OPEN_UNSUPPORTED",
                Code::EmptyBook => "CODE_EMPTY_BOOK",
                Code::ImageOpen => "CODE_IMAGE_OPEN",
                Code::ContainerCantFindFile => "CODE_CONTAINER_CANT_FIND_FILE",
            }
        }
    }

    ///Create a new one exception
    pub fn new<'a>(
        env: &'a JNIEnv,
        code: Code,
        msg: impl Into<JNIString>,
    ) -> JniResult<JThrowable<'a>> {
        let msg = env.auto_local(env.new_string(msg)?);

        let constructor = CONSTRUCTOR.init(env)?;

        let code = env.get_static_field(constructor.class_obj(), code, "I")?;

        constructor
            .create(&[code, msg.as_obj().into()])
            .map(Into::into)
    }
}
