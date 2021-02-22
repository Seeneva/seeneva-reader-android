#![cfg(target_family = "unix")]

use std::cell::Cell;
use std::io::Error as IoError;
use std::os::unix::io::{AsRawFd, FromRawFd, IntoRawFd, RawFd};

use libc;

/// Helper wrapper around file descriptor to help close it after usage
#[derive(Debug)]
pub struct FileRawFd {
    fd: Cell<RawFd>,
}

impl FileRawFd {
    const NOT_OWNED: RawFd = RawFd::min_value();

    /// Wrap raw file descriptor
    /// Panic if provided [fd] is negative
    pub fn new(fd: RawFd) -> Self {
        assert!(fd >= 0, "File descriptor value can't be negative");

        FileRawFd { fd: Cell::new(fd) }
    }

    /// Duplicate raw file descriptor
    pub fn dup(&self) -> std::io::Result<Self> {
        match unsafe { libc::fcntl(self.fd.get(), libc::F_DUPFD_CLOEXEC, 0) } {
            res_fd if res_fd >= 0 => Ok(Self::new(res_fd)),
            err_code => Err(IoError::from_raw_os_error(err_code)),
        }
    }
}

impl std::io::Read for FileRawFd {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        let size = unsafe { libc::read(self.fd.get(), buf.as_mut_ptr() as *mut _, buf.len()) };

        if size < 0 {
            Err(IoError::from_raw_os_error(size as _))
        } else {
            Ok(size as _)
        }
    }
}

impl std::io::Seek for FileRawFd {
    fn seek(&mut self, pos: std::io::SeekFrom) -> std::io::Result<u64> {
        use std::io::SeekFrom;

        let (offset, whence) = match pos {
            SeekFrom::Current(offset) => (offset as libc::off_t, libc::SEEK_CUR),
            SeekFrom::End(offset) => (offset as libc::off_t, libc::SEEK_END),
            SeekFrom::Start(offset) => (offset as libc::off_t, libc::SEEK_SET),
        };

        let pos = unsafe { libc::lseek(self.fd.get(), offset, whence) };

        if pos < 0 {
            Err(IoError::from_raw_os_error(pos as _))
        } else {
            Ok(pos as _)
        }
    }
}

impl From<std::fs::File> for FileRawFd {
    fn from(file: std::fs::File) -> Self {
        FileRawFd::new(file.into_raw_fd())
    }
}

impl FromRawFd for FileRawFd {
    unsafe fn from_raw_fd(fd: RawFd) -> Self {
        FileRawFd::new(fd)
    }
}

impl IntoRawFd for FileRawFd {
    fn into_raw_fd(self) -> RawFd {
        self.fd.replace(Self::NOT_OWNED)
    }
}

impl AsRawFd for FileRawFd {
    fn as_raw_fd(&self) -> RawFd {
        self.fd.get()
    }
}

impl Drop for FileRawFd {
    fn drop(&mut self) {
        let current_fd = self.fd.get();

        if current_fd != Self::NOT_OWNED {
            let code = unsafe { libc::close(self.fd.get()) };

            if code < 0 {
                panic!(
                    "Can't close file descriptor. Errno: {}",
                    IoError::from_raw_os_error(code)
                )
            }
        }
    }
}
