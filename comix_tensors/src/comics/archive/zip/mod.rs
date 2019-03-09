mod error;

use self::error::ZipError;
use super::{ArchiveFile, ComicContainer, ComicFilesStream};

use std::fs::File;
use std::io::{Cursor, Read};
use std::ops::Deref;
use std::os::unix::io::{FromRawFd, RawFd};
use std::sync::Arc;
use std::thread::current as current_thread;

use zip;

use libc;
use memmap::Mmap;

use tokio::prelude::*;

type ZipResult<T> = ::std::result::Result<T, ZipError>;
type OpenedZipArchive = zip::ZipArchive<Cursor<ArcMmap>>;

/// Helper to clone mmap
#[derive(Clone)]
struct ArcMmap(Arc<Mmap>);

impl AsRef<[u8]> for ArcMmap {
    fn as_ref(&self) -> &[u8] {
        self.0.deref().deref()
    }
}

impl From<Mmap> for ArcMmap {
    fn from(mmap: Mmap) -> Self {
        ArcMmap(Arc::new(mmap))
    }
}

///Entry point to open and stream zip archives
#[derive(Debug, Copy, Clone)]
pub struct ZipArchive(RawFd);

impl ZipArchive {
    ///init new zip archive using file descriptor
    pub fn new(fd: RawFd) -> Self {
        ZipArchive(fd)
    }

    ///Open zip archive
    fn open(&self) -> impl Future<Item = OpenedZipArchiveWrapper, Error = ZipError> {
        let fd = self.0;

        future::lazy(move || {
            let fd = unsafe { libc::dup(fd) };

            debug!("Trying to open zip archive {}", fd);

            let mmap: ArcMmap = unsafe {
                let archive_file: File = File::from_raw_fd(fd);
                Mmap::map(&archive_file)
            }?
            .into();

            debug!("Zip file opened. Len {}", mmap.0.len());

            Ok(zip::ZipArchive::new(Cursor::new(mmap))?.into())
        })
    }

    ///Stream all files in the archive
    fn stream_files(&self) -> impl Stream<Item = ArchiveFile, Error = ZipError> {
        self.open().map(stream::iter_result).flatten_stream()
    }
}

impl ComicContainer for ZipArchive {
    fn files(&self) -> ComicFilesStream {
        Box::new(self.stream_files().from_err())
    }
}

///Wrapper around zip crate
#[derive(Clone)]
struct OpenedZipArchiveWrapper(OpenedZipArchive);

impl From<OpenedZipArchive> for OpenedZipArchiveWrapper {
    fn from(archive: OpenedZipArchive) -> Self {
        OpenedZipArchiveWrapper(archive)
    }
}

impl IntoIterator for OpenedZipArchiveWrapper {
    type Item = ZipResult<ArchiveFile>;
    type IntoIter = ZipArchiveIterator;

    fn into_iter(self) -> Self::IntoIter {
        ZipArchiveIterator {
            archive: self.0,
            current_pos: 0,
        }
    }
}

///Iterator over all zip archives files
#[derive(Clone)]
struct ZipArchiveIterator {
    archive: OpenedZipArchive,
    current_pos: usize,
}

impl ZipArchiveIterator {
    fn files_count(&self) -> usize {
        self.archive.len()
    }
}

impl Iterator for ZipArchiveIterator {
    type Item = ZipResult<ArchiveFile>;

    fn next(&mut self) -> Option<Self::Item> {
        let current_pos = self.current_pos;

        if current_pos == self.files_count() {
            return None;
        }

        debug!(
            "Proceed zip file. Position: {}. Thread: '{:?}'",
            current_pos,
            current_thread().name()
        );

        let res = self
            .archive
            .by_index(current_pos)
            .map_err(ZipError::from)
            .and_then(|file| into_archive_file(file, current_pos));

        self.current_pos += 1;

        Some(res)
    }

    fn size_hint(&self) -> (usize, Option<usize>) {
        (0, Some(self.files_count()))
    }
}

impl ExactSizeIterator for ZipArchiveIterator {}

///Check is provided zip file is directory
fn is_dir(file: &zip::read::ZipFile) -> bool {
    file.name().ends_with('/')
}

///Convert zip file to [ArchiveFile]
fn into_archive_file(mut file: zip::read::ZipFile, pos: usize) -> ZipResult<ArchiveFile> {
    debug!(
        "Proceed zip file '{}'. Thread: {:?}",
        file.name(),
        current_thread().name()
    );

    let is_dir = is_dir(&file);

    let content = if is_dir {
        None
    } else {
        let mut buf = Vec::with_capacity(file.size() as usize);
        file.read_to_end(&mut buf)?;
        Some(buf)
    };

    let name = file.name().to_owned();

    Ok(ArchiveFile {
        pos,
        name,
        is_dir,
        content,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::comics::archive::tests::open_archive_fd;
    use crate::comics::magic::{resolve_file_magic_type, MagicType};

    #[test]
    #[cfg(target_family = "unix")]
    fn test_stream_cbz_archive() {
        let fd = open_zip_fd();

        let mut file_count = 0u32;

        ZipArchive::new(fd).stream_files().wait().for_each(|file| {
            let file = file.unwrap();

            assert_eq!(
                file.content.is_none(),
                file.is_dir,
                "If it's a file it should contain content. Otherwise it should be empty"
            );

            assert_eq!(
                file.is_dir,
                file.name == "image_folder/",
                "Wrong dir detection: {}",
                file.name
            );

            file_count += 1;
        });

        assert_eq!(
            file_count, 11,
            "Wrong number of ZIP archive files. Count {}",
            file_count
        );
    }

    #[test]
    #[cfg(target_family = "unix")]
    fn test_guess_cbz_magic_type() {
        let fd = open_zip_fd();
        let mut file = unsafe { File::from_raw_fd(fd) };

        let res = resolve_file_magic_type(&mut file).unwrap();
        assert_eq!(res, MagicType::ZIP);
    }

    #[cfg(target_family = "unix")]
    fn open_zip_fd() -> RawFd {
        open_archive_fd(&["zip", "comics_test.cbz"])
    }
}
