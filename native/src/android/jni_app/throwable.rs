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

use std::any::Any;
use std::error::Error;

use jni::errors::Result as JniResult;
use jni::objects::JThrowable;
use jni::strings::JNIString;
use jni::JNIEnv;

pub use super::objects::app::error::NativeException::Code as NativeExceptionCode;

/// Helper to represent some Rust type as JNI Throwable message
pub trait AsJniErrMsg<T: ?Sized> {
    /// Represent as JNI Throwable message
    fn as_jni_err_msg(&self) -> JNIString;
}

impl<T: AsRef<str>> AsJniErrMsg<JNIString> for T {
    fn as_jni_err_msg(&self) -> JNIString {
        self.into()
    }
}

impl<T: Error> AsJniErrMsg<&'static dyn Error> for T {
    fn as_jni_err_msg(&self) -> JNIString {
        self.to_string().as_jni_err_msg()
    }
}

impl AsJniErrMsg<Box<dyn Error>> for Box<dyn Error> {
    fn as_jni_err_msg(&self) -> JNIString {
        self.to_string().as_jni_err_msg()
    }
}

impl AsJniErrMsg<Box<dyn Error + Send>> for Box<dyn Error + Send> {
    fn as_jni_err_msg(&self) -> JNIString {
        self.to_string().as_jni_err_msg()
    }
}

impl AsJniErrMsg<Box<dyn Error + Send + Sync>> for Box<dyn Error + Send + Sync> {
    fn as_jni_err_msg(&self) -> JNIString {
        self.to_string().as_jni_err_msg()
    }
}

impl AsJniErrMsg<Box<dyn Any>> for Box<dyn Any> {
    fn as_jni_err_msg(&self) -> JNIString {
        any_as_jni_msg(self)
    }
}

impl AsJniErrMsg<Box<dyn Any + Send>> for Box<dyn Any + Send> {
    fn as_jni_err_msg(&self) -> JNIString {
        any_as_jni_msg(self)
    }
}

impl AsJniErrMsg<Box<dyn Any + Send + Sync>> for Box<dyn Any + Send + Sync> {
    fn as_jni_err_msg(&self) -> JNIString {
        any_as_jni_msg(self)
    }
}

/// Try to get meaningful error message from Rust Any type
fn any_as_jni_msg(any: &dyn Any) -> JNIString {
    any.downcast_ref::<Box<dyn Error>>()
        .map(AsJniErrMsg::as_jni_err_msg)
        .or_else(|| {
            any.downcast_ref::<Box<dyn Error + Send>>()
                .map(AsJniErrMsg::as_jni_err_msg)
        })
        .or_else(|| any.downcast_ref::<&str>().map(AsJniErrMsg::as_jni_err_msg))
        .or_else(|| {
            any.downcast_ref::<String>()
                .map(AsJniErrMsg::as_jni_err_msg)
        })
        .unwrap_or_else(|| "Unknown error occurred".as_jni_err_msg())
}

/// Helper to represent Rust type as JNI Throwable
pub trait AsJniThrowable<T: ?Sized>: AsJniErrMsg<T> {
    /// Init new JNI Throwable from this Rust type
    fn as_jni_throwable<'jni>(
        &self,
        throwable_type: Throwable,
        env: &'jni JNIEnv,
    ) -> JniResult<JThrowable<'jni>>
    where
        Self: Sized,
    {
        throwable_type.new(env, self.as_jni_err_msg())
    }

    /// Init new JNI Throwable from this Rust type
    fn try_as_jni_throwable<'jni>(
        &self,
        throwable_type: Throwable,
        env: &'jni JNIEnv,
    ) -> JThrowable<'jni>
    where
        Self: Sized,
    {
        match self.as_jni_throwable(throwable_type, env) {
            Ok(throwable) => throwable,
            Err(err) => err.abort_jvm(env),
        }
    }

    /// Throw this as new JNI Throwable
    fn throw(&self, throwable_type: Throwable, env: &JNIEnv) -> JniResult<()>
    where
        Self: Sized,
    {
        self.as_jni_throwable(throwable_type, env)
            .and_then(|throwable| {
                env.exception_clear()?;

                env.throw(throwable)
            })
    }

    /// Abort JVM by throwing Fatal Error
    fn abort_jvm(&self, env: &JNIEnv) -> ! {
        env.fatal_error(self.as_jni_err_msg())
    }
}

impl<T: ?Sized, TT: AsJniErrMsg<T>> AsJniThrowable<T> for TT {}

/// Helper trait to convert into Result with [JThrowable] error type
pub trait AsJniThrowableResult<T, C> {
    /// Convert into Result with [JThrowable] error type
    /// It will panic if [JThrowable] cannot be init
    fn err_into_jni_throwable<'jni>(
        self,
        throwable_type: Throwable,
        env: &'jni JNIEnv,
    ) -> Result<T, JThrowable<'jni>>;
}

impl<T, E: AsJniThrowable<C>, C> AsJniThrowableResult<T, C> for Result<T, E> {
    fn err_into_jni_throwable<'jni>(
        self,
        throwable_type: Throwable,
        env: &'jni JNIEnv,
    ) -> Result<T, JThrowable<'jni>> {
        self.map_err(|err| err.try_as_jni_throwable(throwable_type, env))
    }
}

#[derive(Debug, Copy, Clone)]
pub enum Throwable {
    IllegalArgumentException,
    NativeFatalError,
    NativeException(NativeExceptionCode),
}

impl Throwable {
    fn new<'jni>(
        self,
        env: &'jni JNIEnv,
        msg: impl Into<JNIString>,
    ) -> JniResult<JThrowable<'jni>> {
        use super::objects::app::error::*;
        use super::objects::java::error::*;

        match self {
            Self::NativeFatalError => NativeFatalError::new(env, msg),
            Self::IllegalArgumentException => IllegalArgumentException::new(env, msg),
            Self::NativeException(code) => NativeException::new(env, code, msg),
        }
    }
}
