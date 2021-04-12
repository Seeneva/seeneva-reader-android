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

use std::convert::TryFrom;
use std::sync::Arc;

use jni::objects::JObject;
use jni::sys::jboolean;
use jni::JNIEnv;

use file_descriptor::FileRawFd;

use crate::android::jni_app::*;
use crate::android::ndk::bitmap::{Bitmap, PixelFormat};
use crate::comics::comic_image::{
    get_comic_book_raw_page, resize_comic_book_page, ColorModel, GetComicRawImageError,
    ResizeComicImageError,
};
use crate::comics::container::ComicContainer;

use super::{container_result_into_throwable, spawn_task, SpawnTaskResult};

use self::app_objects::comic_page_image_data;

impl TryFrom<PixelFormat> for ColorModel {
    type Error = PixelFormat;

    fn try_from(pixel_format: PixelFormat) -> Result<Self, Self::Error> {
        match pixel_format {
            PixelFormat::RGBA_8888 => Ok(ColorModel::RGBA8888),
            PixelFormat::RGB_565 => Ok(ColorModel::RGB565),
            pixel_format => Err(pixel_format),
        }
    }
}

///Get comic book image without decode it
pub fn get_comic_book_encoded_image<'a>(
    env: &'a JNIEnv,
    fd: FileRawFd,
    image_position: usize,
    task_callback: JObject<'a>,
) -> SpawnTaskResult<'a> {
    spawn_task("get_encoded_page", env, task_callback, move |task, env| {
        info!("Trying to open comic book container");

        let mut container = container_result_into_throwable(ComicContainer::open_fd(fd), env)?;

        task.check()?;

        info!("Trying to get raw comic book page by position");

        let (img_buf, img_w, img_h) =
            get_comic_book_raw_page(&task, &mut container, image_position)
                .map_err(|err| match err {
                    GetComicRawImageError::Cancelled(err) => super::TaskError::from(err),
                    GetComicRawImageError::ImgError(_) => err
                        .try_as_jni_throwable(
                            Throwable::NativeException(NativeExceptionCode::ImageOpen),
                            env,
                        )
                        .into(),
                    GetComicRawImageError::ContainerError(_) => err
                        .try_as_jni_throwable(Throwable::NativeFatalError, env)
                        .into(),
                })
                .and_then(|opt_raw_img| {
                    opt_raw_img.ok_or_else(|| {
                        format!("Can't find page by position: {}", image_position)
                            .try_as_jni_throwable(
                                Throwable::NativeException(
                                    NativeExceptionCode::ContainerCantFindFile,
                                ),
                                env,
                            )
                            .into()
                    })
                })?;

        task.check()?;

        info!("Trying to map RAW comic book page into JNI object");

        app_objects::comic_page_image_data::new(env, img_buf, img_w, img_h)
            .err_into_jni_throwable(Throwable::NativeFatalError, env)
            .map_err(Into::into)
    })
}

/// Decode `encoded_page`
/// Optionally image can be resized using (width, height) from target Android `bitmap`
/// Resize type depends on `resize_fast`
/// Optionally image can be cropped using (x, y, width, height) array `crop`
pub fn decode_img<'a>(
    env: &'a JNIEnv,
    encoded_page: JObject<'a>,
    bitmap: JObject<'a>,
    crop: JObject<'a>,
    resize_fast: jboolean,
    task_callback: JObject<'a>,
) -> SpawnTaskResult<'a> {
    let encoded_page = env.new_global_ref(encoded_page)?;
    let bitmap = env.new_global_ref(bitmap)?;
    let crop = env.new_global_ref(crop)?;

    let task_name = format!("decode_img_{:p}", encoded_page.as_obj().into_inner());

    spawn_task(task_name, env, task_callback, move |task, env| {
        let encoded_page = encoded_page;
        let bitmap = bitmap;

        info!("Trying to get crop and resize image params");

        let crop_params;

        {
            // move it here to drop after use
            let crop = crop;

            let crop_obj = crop.as_obj();

            crop_params = if crop_obj.is_null() {
                None
            } else {
                let mut crop_params = [0; 4];

                unwrap_task_result!(
                    env.get_int_array_region(crop_obj.into_inner(), 0, &mut crop_params),
                    env
                );

                let [x, y, width, height] = crop_params;

                assert!(
                    x >= 0 && y >= 0 && width > 0 && height > 0,
                    "Invalid crop params: [{}, {}, {}, {}]",
                    x,
                    y,
                    width,
                    height
                );

                (x as u32, y as u32, width as u32, height as u32).into()
            }
        }

        task.check()?;

        info!("Trying to get Android Bitmap info");

        let mut bitmap_ndk = Bitmap::new(env, bitmap.as_obj());

        let color_model;
        let resize_params;

        {
            task.check()?;

            // get Android Bitmap information using NDK
            let bitmap_info = unwrap_task_result!(bitmap_ndk.info(), env);

            color_model = unwrap_task_result!(
                ColorModel::try_from(bitmap_info.format).map_err(|pixel_format| {
                    format!(
                        "Unsupported Android bitmap pixel format: {:?}",
                        pixel_format
                    )
                }),
                env
            );

            //Take target image size from provided Android Bitmap
            resize_params = (
                bitmap_info.width,
                bitmap_info.height,
                resize_fast == jni::sys::JNI_TRUE,
            );
        }

        task.check()?;

        let img = {
            let encoded_page = encoded_page;

            info!(
                "Trying to get already decoded image for page {:p}",
                encoded_page.as_obj().into_inner()
            );

            // It is optimization to prevent decode same source image multiple time at
            // small amount of time. (e.g. when crop big images into small pieces)

            // 1. Check if we already has cached decoded image in the JNI object
            // 2. Use cached image if we has it
            // 3. Get cached encoded image data from JNI object
            // 4. Decode it as image
            // 5. Put image into Arc object and cache it into JNI object as Weak reference
            // 6. PROFIT!!!

            //Retrieve Mutex lock to page image data
            let page_img_data_lock = unwrap_task_result!(
                comic_page_image_data::image_data_lock(env, encoded_page.as_obj()),
                env
            );

            match page_img_data_lock.decoded_img() {
                Some(img) => img,
                _ => {
                    let mut page_img_data_lock = page_img_data_lock;

                    info!(
                        "Page's decoded image hasn't been found. Decode and cache image {:p}",
                        encoded_page.as_obj().into_inner()
                    );

                    task.check()?;

                    let img = Arc::new(unwrap_task_result!(
                        image::load_from_memory(page_img_data_lock.encoded_buffer()),
                        env
                    ));

                    task.check()?;

                    page_img_data_lock.set_decoded_img(&img);

                    img
                }
            }
        };

        task.check()?;

        info!("Apply resize and crop params to image");

        let image_buf =
            resize_comic_book_page(&task, &img, color_model, resize_params.into(), crop_params)
                .map_err(|ResizeComicImageError(cancel_err)| super::TaskError::from(cancel_err))?;

        drop(img);

        task.check()?;

        info!("Copy image pixels into Android Bitmap");

        unwrap_task_result!(bitmap_ndk.buffer(), env).copy_from_slice(&image_buf);

        Ok(JObject::null())
    })
}
