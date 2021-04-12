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

use jni::objects::{JObject, JString};
use jni::sys::jfloat;
use jni::JNIEnv;

use tesseract_rs::leptonica::*;
use tesseract_rs::{EngineMode, PageIteratorLevel, PageSegMode, Tesseract};

use crate::android::jni_app::*;
use crate::android::ndk::assets::{AssetManager, AssetMode};
use crate::android::ndk::bitmap::Bitmap;

use super::{spawn_task, SpawnTaskResult};

/// Task to init Tesseract from *.tessdata file using provided Android AssetManager `asset_mgr`
pub fn init_tesseract_from_asset<'a>(
    env: &'a JNIEnv,
    asset_mgr: JObject<'a>,
    tessdata_file_name: JString<'a>,
    tess_lang: JString<'a>,
    task_callback: JObject<'a>,
) -> SpawnTaskResult<'a> {
    let asset_mgr = env.new_global_ref(asset_mgr)?;
    let tessdata_file_name: String = env.get_string(tessdata_file_name)?.into();
    let tess_lang: String = env.get_string(tess_lang)?.into();

    let task_name = format!("init_tesseract_from_{}", tessdata_file_name);

    spawn_task(task_name, env, task_callback, move |task, env| {
        info!("Init Tesseract");

        let tesseract = {
            let asset_mgr = unwrap_task_result!(AssetManager::new(env, asset_mgr.as_obj()), env);

            let mut tessdata_asset = unwrap_task_result!(
                asset_mgr
                    .open(&tessdata_file_name, AssetMode::Buffer)
                    .ok_or_else(|| {
                        format!(
                            "Can't find tessdata Android asset: '{}'",
                            tessdata_file_name
                        )
                    }),
                env
            );

            task.check()?;

            unwrap_task_result!(
                Tesseract::from_reader(&mut tessdata_asset, &tess_lang, EngineMode::LstmOnly),
                env
            )
        };

        task.check()?;

        info!("Tesseract was init");

        app_objects::ml::Tesseract::new(env, tesseract)
            .err_into_jni_throwable(Throwable::NativeFatalError, env)
            .map_err(Into::into)
    })
}

/// Recognise text on provided Android [`bitmap`] using [`tesseract`] instance
/// Words will be filtered using provided minimal confidence [`word_min_conf`] [0., 1.]
pub fn recognise_bitmap<'a>(
    env: &'a JNIEnv,
    tesseract: JObject<'a>,
    bitmap: JObject<'a>,
    word_min_conf: jfloat,
    task_callback: JObject<'a>,
) -> SpawnTaskResult<'a> {
    let task_name = format!("recognise_text_on_bitmap_{:p}", bitmap.into_inner());

    let tesseract = env.new_global_ref(tesseract)?;
    let bitmap = env.new_global_ref(bitmap)?;

    let word_min_conf = word_min_conf * 100.;

    spawn_task(task_name, env, task_callback, move |task, env| {
        let tesseract = tesseract;
        let bitmap = bitmap;

        info!("Trying to get Tesseract from JNI object");

        let mut tesseract = unwrap_task_result!(
            app_objects::ml::Tesseract::get_inner(env, tesseract.as_obj()),
            env
        );

        {
            info!("Trying to preprocess Tesseract src image");

            let mut pix: Pix = {
                let mut bitmap = Bitmap::new(env, bitmap.as_obj());

                let bitmap_info = unwrap_task_result!(bitmap.info(), env);
                let bitmap_buffer = unwrap_task_result!(bitmap.buffer(), env);

                // Create Leptonica Pix instance from Android Bitmap by copy each pixel
                unwrap_task_result!(
                    Pix::from_raw(
                        &mut &*bitmap_buffer,
                        bitmap_info.width,
                        bitmap_info.height,
                        (bitmap_info.stride / bitmap_info.width * 8) as _
                    ),
                    env
                )
            };

            // we do not need source Android bitmap anymore
            drop(bitmap);

            task.check()?;

            // firstly we need to grayscale source image and upscale it
            // this is must have preparations.
            pix = unwrap_task_result!(
                pix.convert_rgb_to_gray_fast()
                    .and_then(|pix| pix.scale_gray_2x_li()),
                env
            );

            // copy image to tesseract. It will apply Otsu threshold method to binarize source image.
            tesseract.set_image_pix(&pix);

            // get black-and-white image from tesseract to apply final operations
            pix = tesseract
                .get_thresholded_image()
                .ok_or_else(|| "Tesseract should have threshold image")
                .err_into_jni_throwable(Throwable::NativeFatalError, env)?;

            // blur will improve some cases
            pix = unwrap_task_result!(
                pix.convert_to_8(false)
                    .and_then(|pix| pix.windowed_mean(2, 2, false, true)),
                env
            );

            tesseract.set_segmentation_mode(PageSegMode::SingleBlock);

            // apply Otsu again
            tesseract.set_image_pix(&pix);
        }

        task.check()?;

        info!("Trying to recognise text on image");

        //recognise text on provided image
        unwrap_task_result!(tesseract.recognise(), env);

        task.check()?;

        let txt = {
            let mut tess_result = unwrap_task_result!(
                tesseract
                    .result_iterator()
                    .ok_or_else(|| "Tesseract 'recognise' should be called first!"),
                env
            );

            //iterate over each word and filter them by confidence and collect into single String
            tess_result
                .as_iter(PageIteratorLevel::Word)
                .take_while(|_| task.check().map_or(false, |_| true))
                .filter_map(|(txt, conf)| {
                    debug!("Word: '{}'. Conf: {:.2}%", txt, conf);

                    if conf >= word_min_conf {
                        Some(txt)
                    } else {
                        None
                    }
                })
                .fold(String::new(), |mut string, txt| {
                    if !string.is_empty() {
                        string.push(' ');
                    }

                    string.push_str(&*txt);

                    string
                })
        };

        task.check()?;

        env.new_string(txt)
            .err_into_jni_throwable(Throwable::NativeFatalError, env)
            .map(Into::into)
            .map_err(Into::into)
    })
}
