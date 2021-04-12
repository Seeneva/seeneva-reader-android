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

#![allow(non_camel_case_types)]

use jni::sys::{jobject, JNIEnv};
use libc::{c_int, c_void};

pub type uint32_t = u32;
pub type int32_t = i32;

/// No format.
pub const ANDROID_BITMAP_FORMAT_NONE: int32_t = 0;
/// Red: 8 bits, Green: 8 bits, Blue: 8 bits, Alpha: 8 bits.
pub const ANDROID_BITMAP_FORMAT_RGBA_8888: int32_t = 1;
/// Red: 5 bits, Green: 6 bits, Blue: 5 bits.
pub const ANDROID_BITMAP_FORMAT_RGB_565: int32_t = 4;
/// Deprecated in API level 13. Because of the poor quality of this configuration, it is advised to use ARGB_8888 instead.
pub const ANDROID_BITMAP_FORMAT_RGBA_4444: int32_t = 7;
/// Alpha: 8 bits.
pub const ANDROID_BITMAP_FORMAT_A_8: int32_t = 8;
/// Each component is stored as a half float.
pub const ANDROID_BITMAP_FORMAT_RGBA_F16: int32_t = 9;

/// Operation was successful.
pub const ANDROID_BITMAP_RESULT_SUCCESS: c_int = 0;
/// Bad parameter.
pub const ANDROID_BITMAP_RESULT_BAD_PARAMETER: c_int = -1;
/// JNI exception occured.
pub const ANDROID_BITMAP_RESULT_JNI_EXCEPTION: c_int = -2;
/// Allocation failed.
pub const ANDROID_BITMAP_RESULT_ALLOCATION_FAILED: c_int = -3;

/// Bitmap info
#[repr(C)]
#[derive(Debug, Default, Copy, Clone)]
pub struct AndroidBitmapInfo {
    /// The bitmap width in pixels.
    pub width: uint32_t,
    /// The bitmap height in pixels.
    pub height: uint32_t,
    /// The number of byte per row.
    pub stride: uint32_t,
    /// The bitmap pixel format.
    pub format: int32_t,
    /// Unused
    pub flags: uint32_t,
}

//https://developer.android.com/ndk/guides/stable_apis#bitmaps
#[link(name = "jnigraphics")]
extern "C" {
    /// Given a java bitmap object, fill out the AndroidBitmapInfo struct for it.
    /// If the call fails, the info parameter will be ignored.
    pub fn AndroidBitmap_getInfo(
        env: *mut JNIEnv,
        jbitmap: jobject,
        info: *mut AndroidBitmapInfo,
    ) -> c_int;

    /**
     * Given a java bitmap object, attempt to lock the pixel address.
     * Locking will ensure that the memory for the pixels will not move
     * until the unlockPixels call, and ensure that, if the pixels had been
     * previously purged, they will have been restored.
     *
     * If this call succeeds, it must be balanced by a call to
     * AndroidBitmap_unlockPixels, after which time the address of the pixels should
     * no longer be used.
     *
     * If this succeeds, *addrPtr will be set to the pixel address. If the call
     * fails, addrPtr will be ignored.
     */
    pub fn AndroidBitmap_lockPixels(
        env: *mut JNIEnv,
        jbitmap: jobject,
        addrPtr: *mut *mut c_void,
    ) -> c_int;

    /**
     * Call this to balance a successful call to AndroidBitmap_lockPixels.
     */
    pub fn AndroidBitmap_unlockPixels(env: *mut JNIEnv, jbitmap: jobject) -> c_int;
}
