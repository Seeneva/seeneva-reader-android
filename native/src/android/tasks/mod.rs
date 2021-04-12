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
use std::sync::Arc;

use jni::errors::Result as JniResult;
use jni::objects::{JObject, JThrowable};
use jni::{Executor as JniExecutor, JNIEnv};

use crate::comics::container::InitComicContainerError;
use crate::task::{new_task, CancelledError, Task};

use super::jni_app::*;

pub use self::{comic_book_image::*, comic_book_metadata::*, ml::*};

macro_rules! unwrap_task_result {
    ($result:expr, $env:expr) => {
        $result.err_into_jni_throwable(Throwable::NativeFatalError, &$env)?
    };
}

mod comic_book_image;
mod comic_book_metadata;
mod ml;

type SpawnTaskResult<'a> = JniResult<JObject<'a>>;

/// Spawn new comic book task in the new thread
fn spawn_task<'jni, N, F>(
    name: N,
    env: &'jni JNIEnv,
    callback: JObject<'jni>,
    f: F,
) -> SpawnTaskResult<'jni>
where
    N: std::fmt::Display + Send + 'static,
    F: for<'jni1> FnOnce(Task, &'jni1 JNIEnv) -> Result<JObject<'jni1>, TaskError<'jni1>>
        + Send
        + 'static,
{
    debug!("Queue new task: '{}'", name);

    let jni_executor = JniExecutor::new(Arc::new(env.get_java_vm()?));

    let callback = env.new_global_ref(callback)?;

    app_objects::task::new(
        env,
        new_task(move |task| {
            let thread_id = std::thread::current().id();

            debug!("Execute task: '{}'. Thread: {:?}", name, thread_id);

            jni_executor
                .with_attached(|env| {
                    let callback = callback;

                    match f(task, env) {
                        Ok(result_object) => {
                            debug!("Send task '{}' result. Thread: {:?}", name, thread_id);
                            send_task_result(env, callback.as_obj(), result_object)
                        }
                        Err(TaskError::Throwable(result_error)) => {
                            debug!("Send task '{}' error. Thread: {:?}", name, thread_id);
                            send_task_error(env, callback.as_obj(), result_error)
                        }
                        Err(TaskError::Cancelled) => {
                            debug!("Task '{}' cancelled. Thread: {:?}", name, thread_id);
                            // do nothing cause task was cancelled
                            Ok(())
                        }
                    }
                })
                .expect("Can't proceed task");

            debug!("Task: '{}' finished. Thread: {:?}", name, thread_id);
        }),
    )
}

#[derive(Copy, Clone)]
enum TaskError<'jni> {
    /// task should return this throwable
    Throwable(JThrowable<'jni>),
    // task was cancelled
    Cancelled,
}

impl<'jni> From<JThrowable<'jni>> for TaskError<'jni> {
    fn from(throwable: JThrowable<'jni>) -> Self {
        Self::Throwable(throwable)
    }
}

impl<'jni> From<CancelledError> for TaskError<'jni> {
    fn from(_: CancelledError) -> Self {
        TaskError::Cancelled
    }
}

impl Debug for TaskError<'_> {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        let msg = match self {
            Self::Throwable(_) => "Throwable",
            Self::Cancelled => "Cancelled",
        };

        writeln!(f, "{}", msg)
    }
}

/// Helper to convert comic book container init errors into JNI Throwable
fn container_result_into_throwable<'jni, T>(
    result: Result<T, InitComicContainerError>,
    env: &'jni JNIEnv,
) -> Result<T, JThrowable<'jni>> {
    result.map_err(|err| {
        let throwable_type = match err {
            InitComicContainerError::Unsupported => {
                Throwable::NativeException(NativeExceptionCode::ContainerOpenUnsupported)
            }
            InitComicContainerError::ContainerError(_) => {
                Throwable::NativeException(NativeExceptionCode::ContainerRead)
            }
            _ => Throwable::NativeFatalError,
        };

        err.try_as_jni_throwable(throwable_type, env)
    })
}
