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

use std::convert::{TryFrom, TryInto};
use std::ffi::CString;
use std::io::{Error as IOError, Read, Result as IOResult, Seek, SeekFrom};
use std::marker::PhantomData;

use jni::objects::JObject;
use jni::JNIEnv;

pub use self::error::*;

#[cfg(not(any(target_pointer_width = "64", target_pointer_width = "32")))]
compile_error!("Android NDK Asset API supports 64 and 32 bit only");

pub mod error;
mod ffi;

#[derive(Debug, Copy, Clone)]
pub enum AssetMode {
    Unknown,
    Random,
    Streaming,
    Buffer,
}

impl AssetMode {
    /// FFI representation
    fn as_native(&self) -> ffi::AssetMode {
        match self {
            Self::Unknown => ffi::AASSET_MODE_UNKNOWN,
            Self::Random => ffi::AASSET_MODE_RANDOM,
            Self::Streaming => ffi::AASSET_MODE_STREAMING,
            Self::Buffer => ffi::AASSET_MODE_BUFFER,
        }
    }
}

/// Wrapper around Android native AssetManager
/// It is singleton so it can be copy/cloned
#[derive(Debug, Copy, Clone)]
pub struct AssetManager<'jni> {
    ptr: *mut ffi::AAssetManager,
    pd: PhantomData<&'jni ()>,
}

impl<'jni> AssetManager<'jni> {
    /// Obtain Android AssetManager from provided JNI object
    pub fn new(env: &'jni JNIEnv, jni_mgr: JObject<'jni>) -> Result<Self> {
        let ptr = unsafe {
            ffi::AAssetManager_fromJava(env.get_native_interface(), jni_mgr.into_inner())
        };

        if ptr.is_null() {
            Err(Error::new_sys_error(
                SysErrorKind::Null,
                Some("Can't obtain native pointer to Android AssetManager"),
            ))
        } else {
            Ok(AssetManager {
                ptr,
                pd: PhantomData,
            })
        }
    }

    /// Open Android Asset by provided [`file_name`]
    #[track_caller]
    pub fn open(&self, file_name: impl AsRef<str>, mode: AssetMode) -> Option<Asset> {
        let file_name =
            CString::new(file_name.as_ref()).expect("Can't convert file name into C string");

        unsafe { ffi::AAssetManager_open(self.ptr, file_name.as_ptr(), mode.as_native()) }
            .try_into()
            .ok()
    }
}

/// Wrapper around single Android native Asset
#[derive(Debug)]
pub struct Asset<'a> {
    ptr: *mut ffi::AAsset,
    pd: PhantomData<&'a ()>,
}

impl Asset<'_> {
    fn from_ptr(ptr: *mut ffi::AAsset) -> Result<Self> {
        if ptr.is_null() {
            Err(Error::new_sys_error(
                SysErrorKind::Null,
                Some("Can't obtain native pointer to Android Asset"),
            ))
        } else {
            Ok(Asset {
                ptr,
                pd: PhantomData,
            })
        }
    }

    pub fn allocated(&self) -> bool {
        (unsafe { ffi::AAsset_isAllocated(self.ptr) } == 1)
    }

    /// Size of the Android Asset
    pub fn size(&self) -> usize {
        unsafe {
            if cfg!(target_pointer_width = "64") {
                ffi::AAsset_getLength64(self.ptr).try_into()
            } else if cfg!(target_pointer_width = "32") {
                ffi::AAsset_getLength(self.ptr).try_into()
            } else {
                unreachable!();
            }
        }
        .expect("Can't get Android Asset size")
    }

    /// Return underlying Asset's buffer
    pub fn buffer(&self) -> Result<&[u8]> {
        let buf = unsafe { ffi::AAsset_getBuffer(self.ptr) };

        if buf.is_null() {
            Err(Error::new_sys_error(
                SysErrorKind::Null,
                "Can't get Android Asset buffer".into(),
            ))
        } else {
            Ok(unsafe { std::slice::from_raw_parts(buf as *const u8, self.size()) })
        }
    }
}

impl Read for Asset<'_> {
    fn read(&mut self, buf: &mut [u8]) -> IOResult<usize> {
        let n = unsafe { ffi::AAsset_read(self.ptr, buf.as_ptr() as _, buf.len() as _) };

        if n < 0 {
            Err(IOError::from_raw_os_error(n))
        } else {
            Ok(n as _)
        }
    }
}

impl Seek for Asset<'_> {
    fn seek(&mut self, pos: SeekFrom) -> IOResult<u64> {
        let (offset, whence) = match pos {
            SeekFrom::Current(offset) => (offset, libc::SEEK_CUR),
            SeekFrom::End(offset) => (offset, libc::SEEK_END),
            SeekFrom::Start(offset) => (offset.try_into().unwrap(), libc::SEEK_SET),
        };

        let n = unsafe {
            if cfg!(target_pointer_width = "64") {
                ffi::AAsset_seek64(self.ptr, offset, whence)
            } else if cfg!(target_pointer_width = "32") {
                ffi::AAsset_seek(self.ptr, offset.try_into().unwrap(), whence).into()
            } else {
                unreachable!();
            }
        };

        if n < 0 {
            Err(IOError::from_raw_os_error(n as _))
        } else {
            Ok(n as _)
        }
    }
}

impl TryFrom<*mut ffi::AAsset> for Asset<'_> {
    type Error = Error;

    fn try_from(ptr: *mut ffi::AAsset) -> std::result::Result<Self, Self::Error> {
        Self::from_ptr(ptr)
    }
}

impl Drop for Asset<'_> {
    fn drop(&mut self) {
        unsafe {
            ffi::AAsset_close(self.ptr);
        }
    }
}
