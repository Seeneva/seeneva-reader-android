use jni::errors::Result as JniResult;
use jni::objects::JObject;
use jni::JNIEnv;
use once_cell::sync::Lazy;
use parking_lot::Mutex;

use super::field_pointer::{set_rust_field, take_rust_field};
use crate::task::TaskHandler;

use super::{constants, JniClassConstructorCache};

static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
    Lazy::new(|| (constants::TASK_TYPE, "()V").into());

pub type Task<'a> = JObject<'a>;

const TASK_PTR_FIELD: &str = "id";

/// wrap [TaskHandler] into JVM Task Class
pub fn new<'a>(env: &'a JNIEnv, handler: TaskHandler) -> JniResult<Task<'a>> {
    let jni_task = CONSTRUCTOR
        .init(env)
        .and_then(|constructor| constructor.create(&[]))?;

    set_rust_field(env, jni_task, TASK_PTR_FIELD, Mutex::new(handler))?;

    Ok(jni_task)
}

/// Drop wrapped [TaskHandler] to cancel bounded task
/// It will block thread until task is locked!
pub fn cancel(env: &JNIEnv, task: Task) -> JniResult<()> {
    debug!("Trying to cancel task: {:p}", task.into_inner());

    // Now, while I do not borrow inner TaskHandler it shouldn't be locked
    // But I can do it in the future. So this block can be a problem...

    take_rust_field::<_, _, Mutex<TaskHandler>, _>(env, task, TASK_PTR_FIELD).map(|_| {
        debug!("Task {:p} is cancelled", task.into_inner());

        ()
    })
}
