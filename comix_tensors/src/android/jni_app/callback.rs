use jni::errors::Result as JniResult;
use jni::objects::{JObject, JThrowable};
use jni::JNIEnv;

///Send task [result] Java object via provided [callback]
pub fn send_task_result(env: &JNIEnv, callback: JObject, result: JObject) -> JniResult<()> {
    env.call_method(
        callback,
        "taskResult",
        "(Ljava/lang/Object;)V",
        &[result.into()],
    )
    .map(|_| ())
}

///Send task [error] Java object via provided [callback]
pub fn send_task_error(env: &JNIEnv, callback: JObject, error: JThrowable) -> JniResult<()> {
    env.call_method(
        callback,
        "taskError",
        "(Ljava/lang/Throwable;)V",
        &[JObject::from(error).into()],
    )
    .map(|_| ())
}
