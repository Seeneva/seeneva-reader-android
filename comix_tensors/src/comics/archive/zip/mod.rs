use std::io::Read;
use std::thread::current as current_thread;

use tokio::prelude::*;
use zip;

use crate::FileRawFd;

use super::{ArchiveFile, ComicContainer, ComicContainerError, ComicFilesStream, FindFileFuture};

use self::error::ZipError;

mod error;

type ZipResult<T> = ::std::result::Result<T, ZipError>;
type OpenedZipArchive = zip::ZipArchive<FileRawFd>;

///Entry point to open and stream zip archives
#[derive(Debug)]
pub struct ZipArchive {
    fd: FileRawFd,
}

impl ZipArchive {
    ///init new zip archive
    pub fn new(fd: FileRawFd) -> Self {
        ZipArchive { fd }
    }

    ///Open zip archive
    fn open(&self) -> impl Future<Item = OpenedZipArchiveWrapper, Error = ZipError> {
        future::result(self.fd.dup())
            .map_err(Into::into)
            .and_then(|fd| zip::ZipArchive::new(fd).map_err(Into::into))
            .map(Into::into)
    }

    ///Stream all files in the archive
    fn stream_files(&self) -> impl Stream<Item = ArchiveFile, Error = ZipError> {
        self.open().map(stream::iter_result).flatten_stream()
    }

    fn file_by_position(
        &self,
        pos: usize,
    ) -> impl Future<Item = Option<ArchiveFile>, Error = ZipError> {
        self.open()
            .map(move |mut archive| archive.find_by_position(pos))
            .and_then(|result| match result {
                Some(Ok(file)) => future::ok(Some(file)),
                Some(Err(e)) => future::err(e),
                None => future::ok(None),
            })
    }
}

impl ComicContainer for ZipArchive {
    fn files(&self) -> ComicFilesStream {
        Box::new(self.stream_files().from_err())
    }

    fn file_by_position(&self, pos: usize) -> FindFileFuture {
        Box::new(self.file_by_position(pos).from_err())
    }
}

///Wrapper around zip crate
struct OpenedZipArchiveWrapper(OpenedZipArchive);

impl From<OpenedZipArchive> for OpenedZipArchiveWrapper {
    fn from(archive: OpenedZipArchive) -> Self {
        OpenedZipArchiveWrapper(archive)
    }
}

impl OpenedZipArchiveWrapper {
    fn find_by_position(&mut self, pos: usize) -> Option<ZipResult<ArchiveFile>> {
        if pos > self.0.len() - 1 {
            info!(
                "Requested zip file position {} is greater than archive len {}",
                pos,
                self.0.len()
            );

            return None;
        }

        Some(file_by_index(&mut self.0, pos))
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

        let res = file_by_index(&mut self.archive, current_pos);

        self.current_pos += 1;

        Some(res)
    }

    fn size_hint(&self) -> (usize, Option<usize>) {
        (0, Some(self.files_count()))
    }
}

impl ExactSizeIterator for ZipArchiveIterator {}

fn file_by_index(archive: &mut OpenedZipArchive, pos: usize) -> ZipResult<ArchiveFile> {
    archive
        .by_index(pos)
        .map_err(ZipError::from)
        .and_then(|file| into_archive_file(file, pos))
}

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
#[cfg(target_family = "unix")]
mod tests {
    //    use crate::comics::archive::tests::open_archive_fd;
    //    use crate::comics::magic::{resolve_file_magic_type, MagicType};
    //
    //    use super::*;

    //    #[test]
    //    fn test_stream_cbz_archive() {
    //        let fd = open_zip_fd();
    //
    //        let mut file_count = 0u32;
    //
    //        ZipArchive::new(fd).stream_files().wait().for_each(|file| {
    //            let file = file.unwrap();
    //
    //            assert_eq!(
    //                file.content.is_none(),
    //                file.is_dir,
    //                "If it's a file it should contain content. Otherwise it should be empty"
    //            );
    //
    //            assert_eq!(
    //                file.is_dir,
    //                file.name == "image_folder/",
    //                "Wrong dir detection: {}",
    //                file.name
    //            );
    //
    //            file_count += 1;
    //        });
    //
    //        assert_eq!(
    //            file_count, 11,
    //            "Wrong number of ZIP archive files. Count {}",
    //            file_count
    //        );
    //    }
    //
    //    #[test]
    //    fn test_guess_cbz_magic_type() {
    //        let fd = open_zip_fd();
    //        let mut file = unsafe { File::from_raw_fd(fd) };
    //
    //        let res = resolve_file_magic_type(&mut file).unwrap();
    //        assert_eq!(res, MagicType::ZIP);
    //    }
    //
    //    #[test]
    //    fn test_find_file_success() {
    //        let fd = open_zip_fd();
    //
    //        let file = ZipArchive::new(fd)
    //            .file_by_position(3)
    //            .wait()
    //            .unwrap()
    //            .unwrap();
    //
    //        assert_eq!(file.name, "pexels-photo-1058770.jpeg", "Wrong file name");
    //    }
    //
    //    #[test]
    //    fn test_find_file_empty() {
    //        let fd = open_zip_fd();
    //
    //        let file = ZipArchive::new(fd).file_by_position(100).wait().unwrap();
    //
    //        assert!(file.is_none());
    //    }

    //    fn open_zip_fd() -> RawFd {
    //        open_archive_fd(&["zip", "comics_test.cbz"])
    //    }
}
