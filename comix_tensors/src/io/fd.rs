use std::cell::Cell;
use std::io::Error as IoError;
use std::os::unix::io::{AsRawFd, FromRawFd, IntoRawFd, RawFd};

#[cfg(target_family = "unix")]
#[derive(Debug)]
pub struct FileRawFd {
    fd: Cell<RawFd>,
}

impl FileRawFd {
    const NOT_OWNED: RawFd = RawFd::min_value();

    pub fn new(fd: RawFd) -> Self {
        if fd < 0 {
            panic!("File descriptor value can't be negative");
        }

        FileRawFd { fd: Cell::new(fd) }
    }

    pub fn dup(&self) -> std::io::Result<Self> {
        match unsafe { libc::fcntl(self.fd.get(), libc::F_DUPFD_CLOEXEC, 0) } {
            -1 => {
                let err = IoError::last_os_error();

                error!("Can't dup file descriptor. Errno: {}", err);

                Err(err)
            }
            res_fd => Ok(Self::new(res_fd)),
        }
    }

    pub fn into_fd(self) -> RawFd {
        self.fd.replace(Self::NOT_OWNED)
    }
}

impl std::io::Read for FileRawFd {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        let size = unsafe { libc::read(self.fd.get(), buf.as_mut_ptr() as *mut _, buf.len()) };

        if size == -1 {
            Err(IoError::last_os_error())
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

        if pos == -1 {
            Err(IoError::last_os_error())
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
            let res = unsafe { libc::close(current_fd) };

            if res == -1 {
                panic!(
                    "Can't close file descriptor. Errno: {}",
                    IoError::last_os_error()
                )
            }
        }
    }
}
