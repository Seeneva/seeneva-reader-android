use jni::objects::JObject;
use jni::JNIEnv;
use tokio::prelude::*;

use crate::android::jni_app::prelude::*;
use crate::comics::prelude::*;
use crate::FileRawFd;

use super::{error::TaskError, execute_task, InitTaskResult};

mod error;

#[derive(Debug, Copy, Clone)]
pub enum ExtractImageType {
    ///Image will be decoded and returned as is
    Default,
    ///Image will be decoded and resized into provided size
    Thumbnail((u32, u32)),
}

pub fn get_comic_book_image<'a>(
    env: &'a JNIEnv,
    fd: FileRawFd,
    image_position: usize,
    image_type: ExtractImageType,
    task_callback: JObject<'a>,
) -> InitTaskResult<'a> {
    let task_future = {
        let img_file_future = future::result(ComicContainerVariant::init(fd))
            .from_err::<TaskError>()
            .and_then(move |container| container.file_by_position(image_position).from_err());

        match image_type {
            ExtractImageType::Default => img_file_future.extract_image(),
            ExtractImageType::Thumbnail((w, h)) => img_file_future.extract_thumbnail(w, h),
        }
    };

    execute_task(
        env,
        task_callback,
        task_future,
        move |env, (img_buffer, img_w, img_h)| {
            app_objects::comic_image_result::new_success(env, &img_buffer, img_w, img_h)
                .map(|obj| obj.into_inner())
        },
    )
}
