use std::ffi::CStr;
use std::io::{Read, Seek, SeekFrom};
use std::path::PathBuf;

use libc;

use file_descriptor::FileRawFd;
use libarchive_sys::ffi;

use crate::archive::builder::Builder;
use crate::entry::Entry;
use crate::error::*;

/// libarchive version number
pub fn version_number() -> i32 {
    unsafe { ffi::archive_version_number() }
}

/// libarchive version as string
pub fn version_string() -> &'static str {
    version_to_str(unsafe { ffi::archive_version_string() }).expect("Shouldn't be null")
}

/// Return detailed libarchive version
pub fn version_details() -> &'static str {
    version_to_str(unsafe { ffi::archive_version_details() }).expect("Shouldn't be null")
}

/// Return [None] if was compiled without it
pub fn zlib_version() -> Option<&'static str> {
    version_to_str(unsafe { ffi::archive_zlib_version() })
}

/// Return [None] if was compiled without it
pub fn liblzma_version() -> Option<&'static str> {
    version_to_str(unsafe { ffi::archive_liblzma_version() })
}

/// Return [None] if was compiled without it
pub fn bzlib_version() -> Option<&'static str> {
    version_to_str(unsafe { ffi::archive_bzlib_version() })
}

/// Return [None] if was compiled without it
pub fn liblz4_version() -> Option<&'static str> {
    version_to_str(unsafe { ffi::archive_liblz4_version() })
}

/// Return [None] if was compiled without it
pub fn libzstd_version() -> Option<&'static str> {
    version_to_str(unsafe { ffi::archive_libzstd_version() })
}

/// Convert raw C string into Rust [str]. Return [None] if pointer is null
fn version_to_str(version: *const std::os::raw::c_char) -> Option<&'static str> {
    if version.is_null() {
        None
    } else {
        unsafe { CStr::from_ptr(version) }
            .to_str()
            .expect("Not UTF-8 string")
            .into()
    }
}

trait AsArchiveResult {
    /// Convert into []
    fn into_result(self, archive: &Archive) -> ArchiveResult<()>
    where
        Self: std::marker::Sized;
}

impl<T: Into<Status>> AsArchiveResult for T {
    fn into_result(self, archive: &Archive) -> ArchiveResult<()>
    where
        Self: std::marker::Sized,
    {
        match self.into() {
            Status::Ok => Ok(()),
            err_status => Err(ArchiveError::new_sys_error(
                SysErrorKind::ArchiveStatus(err_status),
                archive.error_msg(),
            )),
        }
    }
}

/// Wrapper around libarchive
#[derive(Debug)]
pub struct Archive {
    /// libarchive raw pointer
    ptr: *mut ffi::archive,
    /// source of the archive
    source: Source,
}

impl Archive {
    /// Wrap raw pointer into Rust structure
    fn wrap(ptr: *mut ffi::archive, source: Source) -> ArchiveResult<Self> {
        if ptr.is_null() {
            Err(ArchiveError::new_sys_error(
                SysErrorKind::Null,
                Some("Can't wrap Archive pointer"),
            ))
        } else {
            Ok(Archive { ptr, source })
        }
    }

    /// Create new archive reader
    fn new_read(source: Source) -> ArchiveResult<Self> {
        Self::wrap(unsafe { ffi::archive_read_new() }, source)
    }

    /// Returns a error status indicating the reason for the most recent error return
    fn status(&self) -> Status {
        unsafe { ffi::archive_errno(self.ptr) }.into()
    }

    /// Returns a textual error message suitable for display
    fn error_msg(&self) -> Option<&str> {
        let c_str = unsafe {
            let c_str = ffi::archive_error_string(self.ptr);

            if c_str.is_null() {
                None
            } else {
                Some(CStr::from_ptr(c_str))
            }
        }?;

        Some(c_str.to_str().expect("Should have valid UTF-8"))
    }

    /// Create new [Archive] builder
    pub fn builder() -> builder::Builder {
        Builder::new()
    }

    /// Return raw C pointer
    pub(crate) fn as_ptr(&self) -> *mut ffi::archive {
        self.ptr
    }

    /// Return current archive format
    /// Return [None] if no archive entry was read
    pub fn get_format(&self) -> Option<Format> {
        let format_code = unsafe { ffi::archive_format(self.ptr) };

        if format_code == 0 {
            None
        } else {
            Some((format_code as u32).into())
        }
    }

    /// Return current archive format name
    /// Return [None] if no archive entry was read
    pub fn get_format_name(&self) -> Option<&str> {
        let c_ptr = unsafe {
            let c_ptr = ffi::archive_format_name(self.ptr);

            if c_ptr.is_null() {
                None
            } else {
                Some(CStr::from_ptr(c_ptr))
            }
        }?;

        Some(
            c_ptr
                .to_str()
                .expect("Libarchive format should contains correct name"),
        )
    }

    /// Return the file offset (within the uncompressed data stream) where the last header started
    pub fn header_position(&self) -> usize {
        (unsafe { ffi::archive_read_header_position(self.ptr) }) as _
    }

    /// Return current file position
    pub fn current_pos(&self) -> usize {
        (unsafe { ffi::archive_file_count(self.ptr) }) as _
    }

    /// Returns the number of filters in the current pipeline
    pub fn filters_count(&self) -> usize {
        (unsafe { ffi::archive_filter_count(self.ptr) }) as _
    }

    /// Skip next archive entry data. May return [Ok(None)] in case of EOF.
    /// It is called by [next_entry]
    pub fn skip_next_entry_data(&mut self) -> ArchiveResult<Option<()>> {
        let result_status = Status::from(unsafe { ffi::archive_read_data_skip(self.ptr) });

        match result_status {
            Status::EOF => Ok(None),
            status => status.into_result(self).map(|_| Some(())),
        }
    }

    /// same as [next_entry] but allows reuse [entry] instance
    pub fn next_entry_into(&mut self, entry: &mut Entry) -> ArchiveResult<Option<()>> {
        self.next_entry_inner(entry)
    }

    /// Return next archive entry.
    /// [Ok(None)] in case if EOF was reached
    pub fn next_entry(&mut self) -> ArchiveResult<Option<Entry>> {
        let mut entry = Entry::new(Some(self))?;

        self.next_entry_inner(&mut entry)
            .map(|opt| opt.map(|_| entry))
    }

    /// Find archive entry by provided [pos]
    /// Return [Ok(None)] if entry cannot be find or if provided position is less than current header position
    pub fn by_position(&mut self, pos: usize) -> ArchiveResult<Option<Entry>> {
        // we can't seek back or reread header. So return None right away
        if pos <= self.current_pos() {
            return Ok(None);
        }

        let mut entry = Entry::new(Some(self))?;

        while let Some(_) = self.next_entry_into(&mut entry)? {
            if self.current_pos() == pos {
                return Ok(Some(entry));
            }
        }

        // Can't find a file
        Ok(None)
    }

    fn next_entry_inner(&mut self, entry: &mut Entry) -> ArchiveResult<Option<()>> {
        let status =
            Status::from(unsafe { ffi::archive_read_next_header2(self.ptr, entry.as_ptr()) });

        match status {
            Status::EOF => Ok(None),
            status => status.into_result(self).map(|_| Some(())),
        }
    }
}

impl Read for Archive {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        let size =
            unsafe { ffi::archive_read_data(self.ptr, buf.as_mut_ptr() as _, buf.len() as _) };

        if size == 0 || size == Status::EOF as isize {
            Ok(0)
        } else if size > 0 {
            Ok(size as _)
        } else {
            let err = (size as i32)
                .into_result(self)
                .expect_err("It should be error");

            Err(std::io::Error::new(std::io::ErrorKind::Other, err))
        }
    }
}

impl Seek for Archive {
    fn seek(&mut self, pos: SeekFrom) -> std::io::Result<u64> {
        let (offset, whence) = match pos {
            SeekFrom::Start(offset) => (offset as _, libc::SEEK_SET),
            SeekFrom::Current(offset) => (offset as _, libc::SEEK_CUR),
            SeekFrom::End(offset) => (offset as _, libc::SEEK_END),
        };

        let result = unsafe { ffi::archive_seek_data(self.ptr, offset, whence) };

        if result >= 0 {
            Ok(result as _)
        } else {
            let err = (result as i32)
                .into_result(self)
                .expect_err("It should be error");

            Err(std::io::Error::new(std::io::ErrorKind::Other, err))
        }
    }
}

impl Drop for Archive {
    fn drop(&mut self) {
        unsafe { ffi::archive_read_free(self.ptr) }
            .into_result(self)
            .expect("Can't drop archive");
    }
}

unsafe impl Send for Archive {}

/// Source of the archive
#[derive(Debug)]
pub enum Source {
    #[cfg(target_family = "unix")]
    // it will help to close file descriptor after the archive was dropped
    FD(FileRawFd),
    Path(PathBuf),
}

#[cfg(target_family = "unix")]
impl From<FileRawFd> for Source {
    fn from(fd: FileRawFd) -> Self {
        Self::FD(fd)
    }
}

impl From<PathBuf> for Source {
    fn from(path: PathBuf) -> Self {
        Self::Path(path)
    }
}

///Error codes: Use archive_errno() and archive_error_string()
///to retrieve details.  Unless specified otherwise, all functions
///that return 'int' use these codes.
#[derive(Debug, Copy, Clone, Eq, PartialEq)]
#[repr(i32)]
pub enum Status {
    /// Operation was successful.
    Ok = ffi::ARCHIVE_STATUS_OK,
    /// Found end of archive.
    EOF = ffi::ARCHIVE_STATUS_EOF,
    /// Retry might succeed.
    Retry = ffi::ARCHIVE_STATUS_RETRY,
    /// Partial success.
    Warn = ffi::ARCHIVE_STATUS_WARN,
    /// Current operation cannot complete.
    /// For example, if write_header "fails", then you can't push data.
    Failed = ffi::ARCHIVE_STATUS_FAILED,
    /// No more operations are possible.
    /// But if write_header is "fatal," then this archive is dead and useless.
    Fatal = ffi::ARCHIVE_STATUS_FATAL,
}

impl From<i32> for Status {
    fn from(code: i32) -> Self {
        match code {
            x if x == Self::Ok as i32 => Self::Ok,
            x if x == Self::EOF as i32 => Self::EOF,
            x if x == Self::Retry as i32 => Self::Retry,
            x if x == Self::Warn as i32 => Self::Warn,
            x if x == Self::Failed as i32 => Self::Failed,
            x if x == Self::Fatal as i32 => Self::Fatal,
            _ => panic!("Unsupported archive status code: {}", code),
        }
    }
}

// We only need formats for: CB7, CBR, CBZ, CBT
/// Archive supported formats
#[derive(Debug, Copy, Clone, Eq, PartialEq, Hash)]
#[repr(u32)]
pub enum Format {
    SevenZip = ffi::ARCHIVE_FORMAT_7ZIP,
    Rar = ffi::ARCHIVE_FORMAT_RAR,
    Rar5 = ffi::ARCHIVE_FORMAT_RAR_V5,
    Zip = ffi::ARCHIVE_FORMAT_ZIP,
    Tar = ffi::ARCHIVE_FORMAT_TAR,
}

impl From<u32> for Format {
    fn from(code: u32) -> Self {
        match code {
            x if x == Self::SevenZip as _ => Self::SevenZip,
            x if x == Self::Rar as _ => Self::Rar,
            x if x == Self::Rar5 as _ => Self::Rar5,
            x if x == Self::Zip as _ => Self::Zip,
            x if x == Self::Tar as _ => Self::Tar,
            x => panic!("Unsupported archive format code: {}", x),
        }
    }
}

pub mod builder {
    use std::collections::HashSet;
    use std::ffi::CString;

    use super::*;

    #[derive(Debug, Default, Clone)]
    pub struct Builder {
        formats: HashSet<Format>,
    }

    impl Builder {
        const READ_BLOCK: u32 = 10240;

        pub fn new() -> Self {
            Builder::default()
        }

        /// Add supported read format
        pub fn add_format(mut self, format: Format) -> Self {
            self.formats.insert(format);

            self
        }

        //TODO I should create ArchiveReader, ArchiveWriter traits to return them from 'open' functions

        #[track_caller]
        pub fn read(self, source: impl Into<Source>) -> ArchiveResult<Archive> {
            let archive = self.init_read_archive(source.into())?;

            match &archive.source {
                Source::FD(raw_fd) => {
                    use std::os::unix::io::AsRawFd;

                    //Warning! Libarchive won't close provided file descriptor!!!
                    unsafe {
                        ffi::archive_read_open_fd(
                            archive.ptr,
                            raw_fd.as_raw_fd(),
                            Self::READ_BLOCK as _,
                        )
                    }
                }
                Source::Path(path) => {
                    let path = if cfg!(unix) {
                        use std::os::unix::ffi::OsStrExt;
                        CString::new(path.as_os_str().as_bytes())
                    } else {
                        CString::new(path.as_os_str().to_str().expect("Can't get path"))
                    }
                    .expect("Can't get C path");

                    unsafe {
                        ffi::archive_read_open_filename(
                            archive.ptr,
                            path.as_ptr() as _,
                            Self::READ_BLOCK as _,
                        )
                    }
                }
            }
            .into_result(&archive)?;

            Ok(archive)
        }

        /// Init new archive reader and apply read formats
        fn init_read_archive(&self, source: Source) -> ArchiveResult<Archive> {
            let archive = Archive::new_read(source)?;

            //Hmm...Do I need to add filters?
            //Like: ffi::archive_read_support_filter_all(archive.inner)

            // apply read formats
            if self.formats.is_empty() {
                // add all formats to the archive
                unsafe { ffi::archive_read_support_format_all(archive.ptr) }
                    .into_result(&archive)?;
            } else {
                for format in self.formats.iter() {
                    unsafe { ffi::archive_read_support_format_by_code(archive.ptr, *format as _) }
                        .into_result(&archive)?;
                }
            }

            Ok(archive)
        }
    }
}
