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

#![allow(non_camel_case_types, dead_code)]

use jni::sys::{jobject, JNIEnv};
use libc::{c_char, c_void, off64_t, off_t, size_t};

/// Available access modes for opening assets with [`AAssetManager_open`]
pub type AssetMode = i32;

/// No specific information about how data will be accessed
pub const AASSET_MODE_UNKNOWN: AssetMode = 0;
/// Read chunks, and seek forward and backward
pub const AASSET_MODE_RANDOM: AssetMode = 1;
/// Read sequentially, with an occasional forward seek
pub const AASSET_MODE_STREAMING: AssetMode = 2;
/// Caller plans to ask for a read-only buffer with all data
pub const AASSET_MODE_BUFFER: AssetMode = 3;

/// Provides access to a read-only asset.
#[repr(C)]
#[derive(Debug)]
pub struct AAsset {
    _private: [u8; 0],
}

/// Provides access to an application's raw assets by creating [`AAsset`] objects
#[repr(C)]
#[derive(Debug)]
pub struct AAssetManager {
    _private: [u8; 0],
}

// A native [AAssetManager] pointer may be shared across multiple threads
// https://developer.android.com/ndk/reference/group/asset#aassetmanager
unsafe impl Send for AAssetManager {}

//https://developer.android.com/ndk/guides/stable_apis#android_native_application_apis
#[link(name = "android")]
extern "C" {
    ///  Obtain the corresponding native [`AAssetManager`] object
    pub fn AAssetManager_fromJava(env: *mut JNIEnv, jni_mgr: jobject) -> *mut AAssetManager;

    /// Open an asset
    pub fn AAssetManager_open(
        mgr: *mut AAssetManager,
        filename: *const c_char,
        mode: AssetMode,
    ) -> *mut AAsset;

    /// Close the asset, freeing all associated resources
    pub fn AAsset_close(asset: *mut AAsset);

    /// Get a pointer to a buffer holding the entire contents of the assset.
    /// Returns NULL on failure.
    pub fn AAsset_getBuffer(asset: *mut AAsset) -> *const c_void;

    /// Report the total size of the asset data
    pub fn AAsset_getLength(asset: *mut AAsset) -> off_t;

    /// Reports the size using a 64-bit number instead of 32-bit as [`AAsset_getLength`]
    pub fn AAsset_getLength64(asset: *mut AAsset) -> off64_t;

    /// Report the total amount of asset data that can be read from the current position
    pub fn AAsset_getRemainingLength(asset: *mut AAsset) -> off_t;

    /// Uses a 64-bit number instead of a 32-bit number as [`AAsset_getRemainingLength`] does
    pub fn AAsset_getRemainingLength64(asset: *mut AAsset) -> off64_t;

    /// Open a new file descriptor that can be used to read the asset data.
    /// Returns < 0 if direct fd access is not possible (for example, if the asset is compressed).
    pub fn AAsset_openFileDescriptor(
        asset: *mut AAsset,
        out_start: *mut off_t,
        out_length: *mut off_t,
    ) -> i32;

    /// Open a new file descriptor that can be used to read the asset data.
    /// Returns < 0 if direct fd access is not possible (for example, if the asset is compressed).

    pub fn AAsset_openFileDescriptor64(
        asset: *mut AAsset,
        out_start: *mut off64_t,
        out_length: *mut off64_t,
    ) -> i32;

    /// Attempt to read 'count' bytes of data from the current offset.
    /// Returns the number of bytes read, zero on EOF, or < 0 on error.
    pub fn AAsset_read(asset: *mut AAsset, buf: *mut c_void, count: size_t) -> i32;

    /// Seek to the specified offset within the asset data.
    /// ['whence'] uses the same constants as lseek()/fseek().
    /// Returns the new position on success, or (off_t) -1 on error.
    pub fn AAsset_seek(asset: *mut AAsset, offset: off_t, whence: i32) -> off_t;

    /// Seek to the specified offset within the asset data.
    /// ['whence'] uses the same constants as lseek()/fseek().
    /// Uses 64-bit data type for large files as opposed to the 32-bit type used by AAsset_seek.
    /// Returns the new position on success, or (off64_t) -1 on error.
    pub fn AAsset_seek64(asset: *mut AAsset, offset: off64_t, whence: i32) -> off64_t;

    /// Returns whether this asset's internal buffer is allocated in ordinary RAM (i.e. not mmapped).
    pub fn AAsset_isAllocated(asset: *mut AAsset) -> i32;
}
