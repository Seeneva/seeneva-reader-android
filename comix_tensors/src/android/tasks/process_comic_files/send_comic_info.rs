use crate::android::jni_app::{JniCache, ComicsProcessingCallback};
use crate::ComicInfo;

use std::sync::Arc;
use std::thread::current as current_thread;

use jni::errors::Error as JniError;
use jni::objects::GlobalRef;
use jni::JavaVM;

use tokio::prelude::*;

///Send [ComicInfo] via JNI
pub fn send_comic_info(
    vm: Arc<JavaVM>,
    jni_cache: Arc<JniCache<'static>>,
    callback: GlobalRef,
    comic_info: ComicInfo,
) -> impl Future<Item = (), Error = JniError> {
    future::lazy(move || {
        let env = vm.attach_current_thread()?;

        debug!(
            "Trying to send ComicInfo via JNI. Thread: {:?}",
            current_thread().name()
        );

        callback.call_on_comic_info_parsed(&env, &jni_cache, &comic_info)?;

        debug!(
            "The ComicInfo was send via JNI. Thread: {:?}",
            current_thread().name()
        );

        Ok(())
    })
}
