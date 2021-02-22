use std::convert::TryFrom;
use std::convert::TryInto;
use std::ffi::CStr;

use libarchive_sys::ffi;

use crate::error::*;
use crate::Archive;
use std::path::Path;

#[derive(Debug)]
pub struct Entry {
    ptr: *mut ffi::archive_entry,
}

impl Entry {
    /// Wrap raw C pointer into Rust structure
    fn wrap(ptr: *mut ffi::archive_entry) -> ArchiveResult<Self> {
        if ptr.is_null() {
            Err(ArchiveError::new_sys_error(
                SysErrorKind::Null,
                Some("Can't wrap Entry pointer"),
            ))
        } else {
            Ok(Entry { ptr })
        }
    }

    /// Allocate and return a blank struct
    /// You cab optionally pass [archive] to pull character-set conversion information
    pub fn new(archive: Option<&Archive>) -> ArchiveResult<Self> {
        unsafe {
            if let Some(archive) = archive {
                ffi::archive_entry_new2(archive.as_ptr())
            } else {
                ffi::archive_entry_new()
            }
        }
        .try_into()
    }

    /// Return raw C pointer
    pub(crate) fn as_ptr(&self) -> *mut ffi::archive_entry {
        self.ptr
    }

    /// Erases the	object,	resetting all internal fields to the same
    /// state as a	newly-created object.  This is provided	to allow you
    /// to	quickly	recycle	objects	without	thrashing the heap.
    fn clear(&mut self) {
        let ptr = unsafe { ffi::archive_entry_clear(self.ptr) };

        if ptr.is_null() {
            panic!("Entry cannot be null");
        }
    }

    /// Entry path inside an archive
    pub fn path(&self) -> &str {
        unsafe { CStr::from_ptr(ffi::archive_entry_pathname_utf8(self.ptr)) }
            .to_str()
            .expect("Should have valid UTF-8")
    }

    /// Entry name inside an archive
    pub fn name(&self) -> &str {
        Path::new(self.path())
            .file_name()
            .expect("Entry should have file name")
            .to_str()
            .expect("Entry file name should contains valid UTF-8")
    }

    /// Returns the file size, if it has been set, and 0 otherwise.
    pub fn file_size(&self) -> usize {
        (unsafe { ffi::archive_entry_size(self.ptr) }) as _
    }

    /// Get file type
    pub fn file_type(&self) -> FileType {
        (unsafe { ffi::archive_entry_filetype(self.ptr) }).into()
    }

    /// Get UNIX timestamp for file last access time
    pub fn get_access_time(&self) -> isize {
        Self::from_c_time(unsafe { ffi::archive_entry_atime(self.ptr) })
    }

    /// Get UNIX timestamp for file last modify time
    pub fn get_modify_time(&self) -> isize {
        Self::from_c_time(unsafe { ffi::archive_entry_mtime(self.ptr) })
    }

    /// Get UNIX timestamp for file last change time
    pub fn get_change_time(&self) -> isize {
        Self::from_c_time(unsafe { ffi::archive_entry_ctime(self.ptr) })
    }

    /// Get UNIX timestamp for file birth time
    pub fn get_birth_time(&self) -> isize {
        Self::from_c_time(unsafe { ffi::archive_entry_birthtime(self.ptr) })
    }

    fn from_c_time(time: libc::time_t) -> isize {
        isize::try_from(time).expect("Can't convert time_t")
    }
}

impl TryFrom<*mut ffi::archive_entry> for Entry {
    type Error = ArchiveError;

    fn try_from(ptr: *mut ffi::archive_entry) -> std::result::Result<Self, Self::Error> {
        Self::wrap(ptr)
    }
}

impl Clone for Entry {
    fn clone(&self) -> Self {
        unsafe { ffi::archive_entry_clone(self.ptr) }
            .try_into()
            .expect("Cannot clone libarchive entry")
    }
}

impl Drop for Entry {
    fn drop(&mut self) {
        unsafe { ffi::archive_entry_free(self.ptr) };
    }
}

unsafe impl Send for Entry {}

/// Entry type
#[derive(Debug, Copy, Clone, Eq, PartialEq)]
pub enum FileType {
    RegularFile,
    Link,
    Socket,
    CharacterFile,
    BlockFile,
    Directory,
    Fifo,
}

impl From<ffi::mode_t> for FileType {
    fn from(file_type: ffi::mode_t) -> Self {
        match file_type {
            ffi::AE_IFREG => FileType::RegularFile,
            ffi::AE_IFLNK => FileType::Link,
            ffi::AE_IFSOCK => FileType::Socket,
            ffi::AE_IFCHR => FileType::CharacterFile,
            ffi::AE_IFBLK => FileType::BlockFile,
            ffi::AE_IFDIR => FileType::Directory,
            ffi::AE_IFIFO => FileType::Fifo,
            _ => panic!("Unsupported file type: {}", file_type),
        }
    }
}
