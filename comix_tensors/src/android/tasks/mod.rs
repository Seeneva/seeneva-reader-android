use std::error::Error;
use std::panic::AssertUnwindSafe;
use std::sync::Arc;

use futures::sync::oneshot::SpawnHandle;
use jni::errors::Error as JniError;
use jni::errors::Result as JniResult;
use jni::objects::JObject;
use jni::{Executor as JniExecutor, JNIEnv};
use tokio::prelude::*;

use crate::future_executor;

use super::jni_app::prelude::*;

mod comic_book_image;
mod comic_book_metadata;
mod error;

pub mod prelude {
    pub use super::cancel_task;
    pub use super::comic_book_image::{
        get_comic_book_image as get_comic_book_image_task, ExtractImageType,
    };
    pub use super::comic_book_metadata::comic_book_metadata as comic_book_metadata_task;
}

///name of Java field which will contain pointer to cancel sender
const TASK_ID_JNI_FIELD: &str = "id";

type InitTaskResult<'a> = Result<JObject<'a>, error::InitTaskError<dyn Error + Send>>;

///Block the thread until provided Future from [f] function not finished
/// Return result of the task
fn execute_task<'a, Mapper, Fut, I>(
    env: &'a JNIEnv,
    task_callback: JObject<'a>,
    future: Fut,
    result_mapper: Mapper,
) -> InitTaskResult<'a>
where
    Mapper: FnOnce(&JNIEnv, I) -> JniResult<jni::sys::jobject> + Send + 'static,
    Fut: Future<Item = I, Error = error::TaskError> + Send + 'static,
    I: Send,
{
    //create a Java Task object for this task
    let task = app_objects::task::new(env)?;

    let task_future = {
        let task_callback = env.new_global_ref(task_callback)?;

        let task = env.new_global_ref(task)?;

        let executor = JniExecutor::new(Arc::new(env.get_java_vm()?));

        AssertUnwindSafe(future).catch_unwind().then(move |result| {
            executor.with_attached(move |env| {
                let task_callback = task_callback;

                let result = {
                    let task = task;

                    //at any case should try to remove pointer from Java object
                    extract_task_handler(&env, task.as_obj())
                        .map(|_| ())
                        .or_else(|e| {
                            match e.0 {
                                //ignore lock error. We will hope that another thread close it properly
                                jni::errors::ErrorKind::TryLock => Ok(()),
                                _ => Err(e),
                            }
                        })
                        .map_err(|e| Box::new(e) as Box<_>)
                        .and(result)
                };

                let result = match result {
                    Ok(Ok(result)) => result_mapper(&env, result).and_then(|result| {
                        callback::send_task_result(
                            &env,
                            task_callback.as_obj(),
                            JObject::from(result),
                        )
                    }),
                    Ok(Err(err)) => err.as_throwable(&env).and_then(|result| {
                        callback::send_task_error(&env, task_callback.as_obj(), result)
                    }),
                    Err(fatal_error) => fatal_error.as_throwable(&env).and_then(|result| {
                        callback::send_task_error(&env, task_callback.as_obj(), result)
                    }),
                };

                result
                    .as_ref()
                    .map_err(|e| e.into_jni_error_wrapper())
                    .jni_fatal_unwrap(env, || "Can't send task result");

                result
            })
        })
    };

    //spawn a new task in the executor
    let task_handle = futures::sync::oneshot::spawn(task_future, &future_executor()?);

    //set pointer to a spawned handler in the Task
    set_task_handler(env, task, task_handle)?;

    Ok(task)
}

///Cancel provided task [task]
pub fn cancel_task(env: &JNIEnv, task: JObject) -> JniResult<bool> {
    info!("Cancel task");

    extract_task_handler(env, task)
        .and_then(|handler| {
            drop(handler);
            info!("Task cancelled");
            Ok(true)
        })
        .or_else(|e| match e.0 {
            //ignore lock error
            jni::errors::ErrorKind::TryLock => Ok(false),
            _ => Err(e),
        })
}

///Set [task_obj] id using pointer to the [cancel_sender]
fn set_task_handler(
    env: &JNIEnv,
    task_id: JObject,
    task_handler: SpawnHandle<(), JniError>,
) -> JniResult<()> {
    env.set_rust_field(task_id, TASK_ID_JNI_FIELD, task_handler)
}

///Extract [CancelSender] from task [task_obj]
fn extract_task_handler(env: &JNIEnv, task_id: JObject) -> JniResult<SpawnHandle<(), JniError>> {
    env.take_rust_field(task_id, TASK_ID_JNI_FIELD)
}
