use std::convert::{TryFrom, TryInto};
use std::ffi::CStr;
use std::os::unix::io::RawFd;

use libc::{c_int, c_uint, size_t};
use once_cell::sync::OnceCell;

use libarchive_sys::ffi;
use libarchive_sys::ffi::{archive, archive_entry};

use crate::error::{ArchiveError, ArchiveResult};

#[derive(Debug)]
pub struct Archive {
    inner: *mut ffi::archive,
    entry_lazy: OnceCell<Entry>,
}

impl Archive {
    /// Create new archive.
    /// Return None if cannot allocate a new archive
    fn new() -> Option<Self> {
        Self::from_inner(unsafe { ffi::archive_read_new() })
    }

    fn from_inner(inner: *mut archive) -> Option<Self> {
        if inner.is_null() {
            None
        } else {
            Archive {
                inner,
                entry_lazy: OnceCell::new(),
            }
            .into()
        }
    }

    fn get_entry(&self) -> ArchiveResult<&Entry> {
        self.entry_lazy
            .get_or_try_init(|| Entry::new(self).ok_or(ArchiveError::Null))
    }

    fn err_no(&self) -> c_int {
        unsafe { ffi::archive_errno(self.inner) }
    }

    /// Return human readable error string
    fn error_msg(&self) -> &str {
        unsafe { CStr::from_ptr(ffi::archive_error_string(self.inner)) }
            .to_str()
            .expect("Should have valid UTF-8")
    }

    /// Return current file position
    pub fn current_pos(&self) -> u32 {
        (unsafe { ffi::archive_file_count(self.inner) }) as u32
    }

    /// Return next archive entry in case of EOF is not reached. Same [Entry] object will be reused.
    pub fn next_entry(&mut self) -> ArchiveResult<Option<&Entry>> {
        let entry = self.get_entry()?;

        let code = unsafe { ffi::archive_read_next_header2(self.inner, entry.inner) };

        match code {
            ffi::ARCHIVE_EOF => Ok(None),
            ffi::ARCHIVE_OK => Ok(entry.into()),
            _ => Err(code_into_error(self)),
        }
    }

    // pub fn read_data(&self) -> ArchiveResult<Option<Vec<u8>>> {
    //     //return empty data in case not inited Entry
    //     match self.entry_lazy.get() {
    //         None => return Ok(None),
    //         Some(e) if e.file_size() == 0 => return Ok(None),
    //         _ => {
    //             let mut buff = std::ptr::null();
    //             let mut size = 0;
    //             let mut offset = 0;
    //
    //             let mut data = Vec::<u8>::with_capacity(self.get_entry()?.file_size() as _);
    //
    //             loop {
    //                 let code = unsafe {
    //                     ffi::archive_read_data_block(self.inner, &mut buff, &mut size, &mut offset)
    //                 };
    //
    //                 match code {
    //                     ffi::ARCHIVE_EOF => {
    //                         return Ok(if data.len() > 0 { Some(data) } else { None });
    //                     }
    //                     ffi::ARCHIVE_OK => {
    //                         if size > 0 {
    //                             data.extend_from_slice(unsafe {
    //                                 std::slice::from_raw_parts(buff as _, size)
    //                             });
    //                         }
    //                     }
    //                     _ => {
    //                         return Err(code_into_error(self));
    //                     }
    //                 };
    //             }
    //         }
    //     }
    // }

    /// Returns `None` if `self.next_entry` is not called or if entry size is 0
    pub fn read_data(&self) -> Option<Vec<u8>> {
        //return empty data in case not inited Entry
        match self.entry_lazy.get() {
            Some(e) if e.file_size() > 0 => {
                let mut data: Vec<u8> = Vec::with_capacity(e.file_size() as _);

                unsafe {
                    let size =
                        ffi::archive_read_data(self.inner, data.as_mut_ptr() as _, data.capacity());
                    data.set_len(size as _);
                };

                data.into()
            }
            _ => None,
        }
    }
}

impl TryFrom<*mut ffi::archive> for Archive {
    type Error = ArchiveError;

    fn try_from(inner: *mut archive) -> Result<Self, Self::Error> {
        Self::from_inner(inner).ok_or(ArchiveError::Null)
    }
}

impl Drop for Archive {
    fn drop(&mut self) {
        let res = code_into_result(unsafe { ffi::archive_read_free(self.inner) }, self);

        if let Err(e) = res {
            panic!("{}", e);
        }
    }
}

fn code_into_result(code: c_int, archive: &Archive) -> ArchiveResult<()> {
    if code == ffi::ARCHIVE_OK {
        return Ok(());
    }

    Err(code_into_error(archive))
}

fn code_into_error(archive: &Archive) -> ArchiveError {
    ArchiveError::Sys(archive.err_no(), archive.error_msg().to_owned()).into()
}

#[derive(Debug)]
pub struct Entry {
    inner: *mut ffi::archive_entry,
}

impl Entry {
    fn new(archive: &Archive) -> Option<Self> {
        Self::from_archive_inner(archive.inner)
    }

    /// Return None if cannot allocate
    fn from_inner(inner: *mut ffi::archive_entry) -> Option<Self> {
        if inner.is_null() {
            None
        } else {
            Some(Entry { inner })
        }
    }

    fn from_archive_inner(archive: *mut ffi::archive) -> Option<Self> {
        Self::from_inner(unsafe { ffi::archive_entry_new2(archive) })
    }

    fn clear(&mut self) {
        let inner_new = unsafe { ffi::archive_entry_clear(self.inner) };

        if inner_new.is_null() {
            panic!("Cannot clear libarchive entry");
        }

        self.inner = inner_new;
    }

    /// Entry file path
    pub fn file_path(&self) -> &str {
        unsafe { CStr::from_ptr(ffi::archive_entry_pathname_utf8(self.inner)) }
            .to_str()
            .expect("Should have valid UTF-8")
    }

    pub fn file_size(&self) -> u32 {
        (unsafe { ffi::archive_entry_size(self.inner) }) as _
    }

    pub fn file_type(&self) -> Type {
        (unsafe { ffi::archive_entry_filetype(self.inner) }).into()
    }
}

impl TryFrom<*mut ffi::archive_entry> for Entry {
    type Error = ArchiveError;

    fn try_from(inner: *mut archive_entry) -> Result<Self, Self::Error> {
        Self::from_inner(inner).ok_or(ArchiveError::Null)
    }
}

impl Clone for Entry {
    fn clone(&self) -> Self {
        unsafe { ffi::archive_entry_clone(self.inner) }
            .try_into()
            .expect("Cannot clone libarchive entry")
    }
}

impl Drop for Entry {
    fn drop(&mut self) {
        unsafe { ffi::archive_entry_free(self.inner) };
    }
}

unsafe impl Send for Archive {}
unsafe impl Send for Entry {}

/// Entry type
#[derive(Debug, Copy, Clone, Eq, PartialEq)]
pub enum Type {
    RegularFile,
    Link,
    Socket,
    CharacterFile,
    BlockFile,
    Directory,
    Fifo,
    Other(ffi::__LA_MODE_T),
}

impl From<ffi::__LA_MODE_T> for Type {
    fn from(t: ffi::__LA_MODE_T) -> Self {
        match t as c_uint {
            ffi::AE_IFREG => Type::RegularFile,
            ffi::AE_IFLNK => Type::Link,
            ffi::AE_IFSOCK => Type::Socket,
            ffi::AE_IFCHR => Type::CharacterFile,
            ffi::AE_IFBLK => Type::BlockFile,
            ffi::AE_IFDIR => Type::Directory,
            ffi::AE_IFIFO => Type::Fifo,
            _ => Type::Other(t),
        }
    }
}

pub mod builder {
    use super::*;

    #[derive(Debug, Default, Copy, Clone)]
    pub struct Builder {
        formats: u8,
        filters: u8,
    }

    impl Builder {
        const READ_BLOCK: size_t = 10240;

        pub fn new() -> Self {
            Builder::default()
        }

        pub fn add_format_7zip(&mut self) -> &mut Self {
            self.add_format(Format::SevenZip)
        }

        pub fn add_format_rar(&mut self) -> &mut Self {
            self.add_format(Format::Rar)
        }

        pub fn add_format_rar5(&mut self) -> &mut Self {
            self.add_format(Format::Rar5)
        }

        pub fn add_format_zip(&mut self) -> &mut Self {
            self.add_format(Format::Zip)
        }

        pub fn add_format_tar(&mut self) -> &mut Self {
            self.add_format(Format::Tar)
        }

        fn add_format(&mut self, format: Format) -> &mut Self {
            self.formats |= format as u8;
            self
        }

        /// Open archive by privided file descriptor
        #[cfg(target_family = "unix")]
        pub fn open_fd(self, fd: RawFd) -> ArchiveResult<Archive> {
            Archive::new()
                .ok_or(ArchiveError::Null)
                .and_then(|archive| {
                    //Hmm...Do I need to add filters?
                    //Like: ffi::archive_read_support_filter_all(archive.inner)

                    self.apply_formats(&archive)
                        .and_then(|_| {
                            code_into_result(
                                unsafe {
                                    ffi::archive_read_open_fd(archive.inner, fd, Self::READ_BLOCK)
                                },
                                &archive,
                            )
                        })
                        .map(|_| archive)
                })
        }

        fn has_format(&self, format: Format) -> bool {
            self.formats & format as u8 == format as u8
        }

        /// Apply provided formats to libarchive
        fn apply_formats(&self, archive: &Archive) -> ArchiveResult<()> {
            if self.has_format(Format::SevenZip) {
                code_into_result(
                    unsafe { ffi::archive_read_support_format_7zip(archive.inner) },
                    archive,
                )?;
            }
            if self.has_format(Format::Rar) {
                code_into_result(
                    unsafe { ffi::archive_read_support_format_rar(archive.inner) },
                    archive,
                )?;
            }
            if self.has_format(Format::Rar5) {
                code_into_result(
                    unsafe { ffi::archive_read_support_format_rar5(archive.inner) },
                    archive,
                )?;
            }
            if self.has_format(Format::Zip) {
                code_into_result(
                    unsafe { ffi::archive_read_support_format_zip(archive.inner) },
                    archive,
                )?;
            }
            if self.has_format(Format::Tar) {
                code_into_result(
                    unsafe { ffi::archive_read_support_format_tar(archive.inner) },
                    archive,
                )?;
            };

            Ok(())
        }
    }

    // We only need formats for: CB7, CBR, CBZ, CBT
    #[repr(u8)]
    #[derive(Debug, Copy, Clone)]
    enum Format {
        Empty = 0,
        SevenZip = 1 << 0,
        Rar = 1 << 1,
        Rar5 = 1 << 2,
        Zip = 1 << 3,
        Tar = 1 << 4,
    }
}
