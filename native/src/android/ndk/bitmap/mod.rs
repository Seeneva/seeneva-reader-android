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

use std::ops::{Deref, DerefMut};

use jni::objects::JObject;
use jni::JNIEnv;
use libc::c_int;

use self::error::*;

pub mod error;
mod ffi;

/// Convert code result into Rust Result
fn code_to_result(code: c_int) -> Result<()> {
    match Status::from(code) {
        Status::Success => Ok(()),
        status => Err(status.into()),
    }
}

pub struct Bitmap<'jni> {
    env: &'jni JNIEnv<'jni>,
    bitmap: JObject<'jni>,
}

impl<'jni> Bitmap<'jni> {
    /// Wrap from JNI
    pub fn new(env: &'jni JNIEnv<'jni>, bitmap: JObject<'jni>) -> Self {
        Bitmap { env, bitmap }
    }

    /// Get underlying Android Bitmap info
    pub fn info(&self) -> Result<BitmapInfo> {
        BitmapInfo::new(self.env, self.bitmap)
    }

    /// Get underlying Android Bitmap buffer
    pub fn buffer<'a>(&'a mut self) -> Result<impl DerefMut<Target = [u8]> + 'a> {
        BitmapBuffer::open(self.env, self.bitmap, &self.info()?)
    }
}

/// Represent mutable Android Bitmap buffer
struct BitmapBuffer<'jni> {
    env: &'jni JNIEnv<'jni>,
    _bitmap_monitor: jni::MonitorGuard<'jni>,
    bitmap: JObject<'jni>,
    buf: &'jni mut [u8],
}

impl<'jni> BitmapBuffer<'jni> {
    /// Open new bitmap buffer
    fn open(env: &'jni JNIEnv, bitmap: JObject<'jni>, info: &BitmapInfo) -> Result<Self> {
        //Lock source bitmap
        let bitmap_monitor = env.lock_obj(bitmap)?;

        let buf = unsafe {
            let mut buf = std::ptr::null_mut();

            code_to_result(ffi::AndroidBitmap_lockPixels(
                env.get_native_interface(),
                bitmap.into_inner(),
                &mut buf,
            ))?;

            std::slice::from_raw_parts_mut(buf as *mut _, (info.stride * info.height) as _)
        };

        Ok(BitmapBuffer { env, _bitmap_monitor: bitmap_monitor, bitmap, buf })
    }
}

impl Deref for BitmapBuffer<'_> {
    type Target = [u8];

    fn deref(&self) -> &Self::Target {
        self.buf
    }
}

impl DerefMut for BitmapBuffer<'_> {
    fn deref_mut(&mut self) -> &mut Self::Target {
        self.buf
    }
}

impl Drop for BitmapBuffer<'_> {
    fn drop(&mut self) {
        code_to_result(unsafe {
            ffi::AndroidBitmap_unlockPixels(
                self.env.get_native_interface(),
                self.bitmap.into_inner(),
            )
        })
        .expect("Can't unlock bitmap buffer");
    }
}

#[derive(Debug, Copy, Clone)]
pub struct BitmapInfo {
    /// The bitmap width in pixels.
    pub width: u32,
    /// The bitmap height in pixels.
    pub height: u32,
    /// The number of byte per row.
    pub stride: u32,
    /// The bitmap pixel format.
    pub format: PixelFormat,
}

impl BitmapInfo {
    /// Get Android bitmap info
    fn new(env: &JNIEnv, bitmap: JObject) -> Result<Self> {
        let mut info = ffi::AndroidBitmapInfo::default();

        code_to_result(unsafe {
            ffi::AndroidBitmap_getInfo(env.get_native_interface(), bitmap.into_inner(), &mut info)
        })?;

        Ok(info.into())
    }
}

impl From<ffi::AndroidBitmapInfo> for BitmapInfo {
    fn from(info: ffi::AndroidBitmapInfo) -> Self {
        let ffi::AndroidBitmapInfo {
            width,
            height,
            stride,
            format,
            ..
        } = info;

        BitmapInfo {
            width,
            height,
            stride,
            format: format.into(),
        }
    }
}

#[allow(non_camel_case_types)]
#[derive(Debug, Copy, Clone)]
pub enum PixelFormat {
    None,
    RGBA_8888,
    RGB_565,
    RGBA_4444,
    A_8,
    RGBA_F16,
}

impl From<ffi::int32_t> for PixelFormat {
    fn from(format: i32) -> Self {
        match format {
            ffi::ANDROID_BITMAP_FORMAT_NONE => PixelFormat::None,
            ffi::ANDROID_BITMAP_FORMAT_RGBA_8888 => PixelFormat::RGBA_8888,
            ffi::ANDROID_BITMAP_FORMAT_RGB_565 => PixelFormat::RGB_565,
            ffi::ANDROID_BITMAP_FORMAT_RGBA_4444 => PixelFormat::RGBA_4444,
            ffi::ANDROID_BITMAP_FORMAT_A_8 => PixelFormat::A_8,
            ffi::ANDROID_BITMAP_FORMAT_RGBA_F16 => PixelFormat::RGBA_F16,
            format => panic!("Unknown bitmap format '{}'", format),
        }
    }
}

/// Describes Android bitmap status
#[derive(Debug, Copy, Clone)]
#[repr(i32)]
pub enum Status {
    Success = ffi::ANDROID_BITMAP_RESULT_SUCCESS,
    AllocationFailed = ffi::ANDROID_BITMAP_RESULT_ALLOCATION_FAILED,
    BadParameter = ffi::ANDROID_BITMAP_RESULT_BAD_PARAMETER,
    JniException = ffi::ANDROID_BITMAP_RESULT_JNI_EXCEPTION,
}

impl From<i32> for Status {
    fn from(code: i32) -> Self {
        match code {
            code if code == Self::Success as _ => Self::Success,
            code if code == Self::AllocationFailed as _ => Self::AllocationFailed,
            code if code == Self::BadParameter as _ => Self::BadParameter,
            code if code == Self::JniException as _ => Self::JniException,
            code => panic!("Unsupported Android bitmap result code: {}", code),
        }
    }
}
