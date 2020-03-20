use std::borrow::Cow;
use std::ffi::CStr;

use libarchive_sys::ffi;

pub use self::archive::{
    builder::Builder as ArchiveBuilder, Archive, Entry as ArchiveEntry, Type as ArchiveEntryType,
};

pub mod archive;
pub mod error;

/// Return detailed libarchive version
pub fn version() -> Cow<'static, str> {
    unsafe { CStr::from_ptr(ffi::archive_version_details()) }.to_string_lossy()
}
