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
use std::sync::{Arc, Weak};

use jni::errors::Result as JniResult;
use jni::objects::JObject;
use jni::sys::jint;
use jni::JNIEnv;
use once_cell::sync::Lazy;
use parking_lot::{Mutex, MutexGuard};

use super::field_pointer::{get_rust_field, set_rust_field, take_rust_field};
use super::{constants, JniClassConstructorCache};

const DATA_PTR_FIELD: &str = "dataPtr";

static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
    Lazy::new(|| (constants::Results::COMIC_PAGE_IMAGE_DATA_TYPE, "(II)V").into());

pub type ComicPageImageData<'a> = JObject<'a>;

type PageImageDataLock = Mutex<PageImageData>;

/// Describes page image data
#[derive(Clone)]
pub struct PageImageData {
    /// Encoded buffer of the page' image
    encoded_buffer: Vec<u8>,
    /// Weak reference to decoded [encoded_buffer]
    decoded_img: Option<Weak<image::DynamicImage>>,
}

impl PageImageData {
    fn new(encoded_buffer: Vec<u8>) -> Self {
        PageImageData {
            encoded_buffer,
            decoded_img: None,
        }
    }

    /// Return underlying encoded image buffer
    pub fn encoded_buffer(&self) -> &[u8] {
        &self.encoded_buffer
    }

    /// Return underlying reference to decoded image if any
    pub fn decoded_img(&self) -> Option<Arc<image::DynamicImage>> {
        self.decoded_img
            .as_ref()
            .and_then(|weak_img| weak_img.upgrade())
    }

    /// Set reference to decoded image
    /// Return previously set [decoded_img] if any
    pub fn set_decoded_img(
        &mut self,
        img: &Arc<image::DynamicImage>,
    ) -> Option<Weak<image::DynamicImage>> {
        self.decoded_img.replace(Arc::downgrade(img))
    }
}

impl Debug for PageImageData {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        f.debug_struct("PageImageData")
            .field("encoded_buffer", &self.encoded_buffer)
            .field(
                "decoded_img_strong_count",
                &self.decoded_img.as_ref().map(Weak::strong_count),
            )
            .finish()
    }
}

///New success encoded comic book page image data
pub fn new<'a>(
    env: &'a JNIEnv,
    img_buffer: Vec<u8>,
    img_w: u32,
    img_h: u32,
) -> JniResult<ComicPageImageData<'a>> {
    let img_w = img_w as jint;
    let img_h = img_h as jint;

    let jni_encoded_img = CONSTRUCTOR
        .init(env)
        .and_then(|constructor| constructor.create(&[img_w.into(), img_h.into()]))?;

    set_rust_field(
        env,
        jni_encoded_img,
        DATA_PTR_FIELD,
        Mutex::new(PageImageData::new(img_buffer)),
    )?;

    Ok(jni_encoded_img)
}

/// Close encoded comic book page image data
pub fn close(env: &JNIEnv, encoded_image: ComicPageImageData) -> JniResult<()> {
    debug!(
        "Trying to close ComicPageImageData: {:p}",
        encoded_image.into_inner()
    );

    take_rust_field::<_, _, PageImageDataLock, _>(env, encoded_image, DATA_PTR_FIELD)?;

    debug!(
        "ComicPageImageData {:p} was closed",
        encoded_image.into_inner()
    );

    Ok(())
}

/// Get underlying encoded image data lock
pub fn image_data_lock<'jni>(
    env: &'jni JNIEnv,
    encoded_image: ComicPageImageData<'jni>,
) -> JniResult<MutexGuard<'jni, PageImageData>> {
    get_rust_field::<_, _, PageImageDataLock, _>(env, encoded_image, DATA_PTR_FIELD)
        .map(|field| field.lock())
}
