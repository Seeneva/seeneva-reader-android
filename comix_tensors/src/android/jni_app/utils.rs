use std::any::Any;
use std::borrow::Cow;
use std::error::Error;
use std::fmt::Display;
use std::ops::Deref;
use std::sync::Arc;

use jni::errors::Result as JniResult;
use jni::objects::JThrowable;
use jni::{Executor as JniExecutor, JNIEnv, JavaVM};

use error_unwrap_helpers::{IntoJniErrorWrapper, JniErrorSource};

///Used to convert objects into Java [JThrowable]
pub trait AsJThrowable {
    ///Create [JThrowable] from this object
    fn as_throwable<'a>(&self, env: &'a JNIEnv) -> JniResult<JThrowable<'a>>;
}

//Helper to convert from specific error into Box
impl<T: AsJThrowable + Send + 'static> From<T> for Box<dyn AsJThrowable + Send> {
    fn from(input: T) -> Self {
        Box::new(input)
    }
}

impl AsJThrowable for jni::errors::Error {
    fn as_throwable<'a>(&self, env: &'a JNIEnv) -> JniResult<JThrowable<'a>> {
        as_jni_fatal_error(env, self.into_jni_error_wrapper())
    }
}

impl AsJThrowable for Box<dyn Any> {
    fn as_throwable<'a>(&self, env: &'a JNIEnv) -> JniResult<JThrowable<'a>> {
        as_jni_fatal_error(env, self.deref().into_jni_error_wrapper())
    }
}

impl AsJThrowable for Box<dyn Any + Send> {
    fn as_throwable<'a>(&self, env: &'a JNIEnv) -> JniResult<JThrowable<'a>> {
        as_jni_fatal_error(env, self.deref().into_jni_error_wrapper())
    }
}

impl AsJThrowable for Box<dyn Any + Send + Sync> {
    fn as_throwable<'a>(&self, env: &'a JNIEnv) -> JniResult<JThrowable<'a>> {
        as_jni_fatal_error(env, self.deref().into_jni_error_wrapper())
    }
}

///Create [NativeFatalError] from the provided [input]
pub fn as_jni_fatal_error<'a, Input>(env: &'a JNIEnv, input: Input) -> JniResult<JThrowable<'a>>
where
    Input: error_unwrap_helpers::JniErrorSource,
{
    use super::objects::app::error::NativeFatalError;

    NativeFatalError::new(env, input.as_jni_error_msg())
}

///Return global panic handler function which will try to send each panic as fatal error via jni
pub fn jni_panic_handler(vm: Arc<JavaVM>) -> impl Fn(Box<dyn Any + Send>) {
    let executor = JniExecutor::new(vm);

    move |panic| {
        let panic_reader = panic.into_jni_error_wrapper();
        let err_text = panic_reader.as_jni_error_msg();

        executor
            .with_attached::<_, ()>(|env| {
                env.fatal_error(
                    jni_error_msg_comment("Tokio panic handler called", &err_text).to_string(),
                );
            })
            .expect("Can't send Tokio panic as fatal error via jni");
    }
}

///Create JNI error message with user comment
fn jni_error_msg_comment<U, E>(user_comment: &U, error_msg: &E) -> String
where
    U: Display + ?Sized,
    E: Display + ?Sized,
{
    format!("{}. Error cause: {}", user_comment, error_msg)
}

///Helpers to unwrap standard Rust's [Result] and throw Java's Throwable
mod error_unwrap_helpers {
    use super::*;
    use crate::android::jni_app::prelude::throw_native_fatal_error;
    use std::borrow::Borrow;
    use std::marker::PhantomData;

    ///Helper to convert data into wrapper to get JNI's error info
    pub trait IntoJniErrorWrapper<R: JniErrorSource> {
        ///Convert into a wrapper
        fn into_jni_error_wrapper(self) -> R;
    }

    impl<T, O: ?Sized> IntoJniErrorWrapper<ErrorDynWrapper<T, O>> for T
    where
        ErrorDynWrapper<T, O>: JniErrorSource,
    {
        fn into_jni_error_wrapper(self) -> ErrorDynWrapper<T, O> {
            ErrorDynWrapper::new(self)
        }
    }

    ///Wrapper around trait object which can be used to get JNI's error info
    pub struct ErrorDynWrapper<T, O: ?Sized>(T, PhantomData<O>);

    impl<T, O: ?Sized> ErrorDynWrapper<T, O> {
        ///Create a new wrapper from a provided data
        fn new(data: T) -> Self {
            ErrorDynWrapper(data, PhantomData)
        }
    }

    ///Helper to get all needed JNI's error info
    pub trait JniErrorSource {
        ///Get JNI's error message from the current data
        fn as_jni_error_msg(&self) -> Cow<str>;
    }

    //Errors do not need any wrapper
    impl<T: Error> JniErrorSource for T {
        fn as_jni_error_msg(&self) -> Cow<str> {
            error_as_jni_msg(self).into()
        }
    }

    impl<T: Borrow<dyn Any>> JniErrorSource for ErrorDynWrapper<T, dyn Any> {
        fn as_jni_error_msg(&self) -> Cow<str> {
            any_as_jni_msg(self.0.borrow())
        }
    }

    impl<T: Borrow<dyn Any + Send>> JniErrorSource for ErrorDynWrapper<T, dyn Any + Send> {
        fn as_jni_error_msg(&self) -> Cow<str> {
            any_as_jni_msg(self.0.borrow())
        }
    }

    impl<T: Borrow<dyn Any + Send + Sync>> JniErrorSource
        for ErrorDynWrapper<T, dyn Any + Send + Sync>
    {
        fn as_jni_error_msg(&self) -> Cow<str> {
            any_as_jni_msg(self.0.borrow())
        }
    }

    impl<'a, T: Error + ?Sized> JniErrorSource for ErrorDynWrapper<&'a T, dyn Error> {
        fn as_jni_error_msg(&self) -> Cow<str> {
            error_as_jni_msg(self.0).into()
        }
    }

    impl<T: Error + ?Sized> JniErrorSource for ErrorDynWrapper<Box<T>, dyn Error> {
        fn as_jni_error_msg(&self) -> Cow<str> {
            error_as_jni_msg(self.0.deref()).into()
        }
    }

    /// Tries to get meaningful description from panic
    fn any_as_jni_msg(any: &dyn Any) -> Cow<str> {
        if let Some(error) = any.downcast_ref::<Box<dyn Error>>() {
            error_as_jni_msg(&**error).into()
        } else if let Some(error) = any.downcast_ref::<Box<dyn Error + Send>>() {
            error_as_jni_msg(&**error).into()
        } else if let Some(s) = any.downcast_ref::<&str>() {
            Cow::Borrowed(s)
        } else if let Some(s) = any.downcast_ref::<String>() {
            s.into()
        } else {
            Cow::Borrowed("Unknown error occured")
        }
    }

    ///Convert error into JNI's error message
    fn error_as_jni_msg<E: Error + ?Sized>(e: &E) -> String {
        format!("{}. {:?}", e, e)
    }

    ///Helper to unwrap results and throws Java's Throwable
    pub trait JavaResultUnwrapper<T, E>
    where
        E: JniErrorSource,
        Self: Into<Result<T, E>>,
    {
        ///Unwrap and throws [NativeFatalError] from this result in case of error
        fn jni_error_unwrap<S: AsRef<str>>(
            self,
            env: &JNIEnv,
            message: impl FnOnce() -> S,
            error_value: impl (FnOnce() -> T),
        ) -> T {
            self.into().unwrap_or_else(|e| {
                let msg = jni_error_msg_comment(message().as_ref(), &e.as_jni_error_msg());

                throw_native_fatal_error(env, &msg);

                error_value()
            })
        }

        ///Unwrap and throws Java fatal error in case of error
        fn jni_fatal_unwrap<S>(self, env: &JNIEnv, message: impl FnOnce() -> S) -> T
        where
            S: AsRef<str>,
        {
            self.into().unwrap_or_else(|e| {
                let msg = jni_error_msg_comment(message().as_ref(), &e.as_jni_error_msg());

                env.fatal_error(&msg)
            })
        }
    }

    impl<T, E> JavaResultUnwrapper<T, E> for Result<T, E> where E: JniErrorSource {}
}

pub mod prelude {
    pub use super::error_unwrap_helpers::{
        IntoJniErrorWrapper, JavaResultUnwrapper, JniErrorSource,
    };
    pub use super::{as_jni_fatal_error, jni_panic_handler, AsJThrowable};
}
