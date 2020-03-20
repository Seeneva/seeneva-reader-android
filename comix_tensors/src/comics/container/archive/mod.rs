use std::ops::{Deref, DerefMut};

use tokio::prelude::*;

use libarchive_rs::{Archive as LibArchive, *};

use crate::comics::container::{ArchiveFile, ComicContainer, ComicFilesStream, FindFileFuture};
use crate::FileRawFd;

pub use self::error::ArchiveError;

mod error;

type ArchiveResult<T> = Result<T, ArchiveError>;

#[derive(Debug)]
pub struct Archive {
    fd: FileRawFd,
}

impl Archive {
    pub fn new(fd: FileRawFd) -> Self {
        Archive { fd }
    }

    /// Open archive
    fn open(&self) -> impl Future<Item = LibArchiveWrapper, Error = ArchiveError> {
        future::result(self.fd.dup())
            .map_err(Into::into)
            .and_then(|fd| LibArchiveWrapper::new(fd).map_err(Into::into))
    }
}

impl ComicContainer for Archive {
    fn files(&self) -> ComicFilesStream {
        Box::new(
            self.open()
                .map(stream::iter_result)
                .flatten_stream()
                .from_err(),
        )
    }

    fn file_by_position(&self, pos: usize) -> FindFileFuture {
        Box::new(
            self.open()
                .and_then(move |mut archive| future::result(archive.file_by_position(pos)))
                .from_err(),
        )
    }
}

#[derive(Debug)]
struct LibArchiveWrapper(LibArchive);

impl LibArchiveWrapper {
    fn new(fd: FileRawFd) -> ArchiveResult<Self> {
        ArchiveBuilder::new()
            .add_format_zip()
            .add_format_tar()
            .add_format_7zip()
            .add_format_rar5()
            .add_format_rar()
            .open_fd(fd.into_fd())
            .map_err(Into::into)
            .map(|inner| LibArchiveWrapper(inner))
    }

    /// Try to find file in the archive by it position.
    ///
    /// Return `Ok(None)` if file cannot be found
    fn file_by_position(&mut self, pos: usize) -> ArchiveResult<Option<ArchiveFile>> {
        loop {
            let file_pos = self.current_pos() as usize;

            match self.next_entry()? {
                None => return Ok(None), //cannot find a file by position
                Some(entry) if file_pos == pos => {
                    return Ok(entry)
                        .map(|e| (e.file_type(), e.file_path().to_owned()))
                        .and_then(|(file_type, file_path)| {
                            build_archive_file(self, file_pos, file_type, file_path)
                        })
                        .map(Into::into)
                }
                _ => continue,
            };
        }
    }
}

/// Build archive file from parts
fn build_archive_file(
    archive: &LibArchive,
    entry_pos: usize,
    entry_type: ArchiveEntryType,
    entry_name: String,
) -> ArchiveResult<ArchiveFile> {
    let is_dir = entry_type == ArchiveEntryType::Directory;

    debug!(
        "File extracted. Pos: '{}'. Name: '{}'. Type: '{:?}'",
        entry_pos, entry_name, entry_type
    );

    let content = if is_dir { None } else { archive.read_data() };

    Ok(ArchiveFile {
        pos: entry_pos,
        name: entry_name,
        is_dir,
        content,
    })
}

impl Deref for LibArchiveWrapper {
    type Target = LibArchive;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl DerefMut for LibArchiveWrapper {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.0
    }
}

impl IntoIterator for LibArchiveWrapper {
    type Item = ArchiveResult<ArchiveFile>;
    type IntoIter = LibArchiveIterator;

    fn into_iter(self) -> Self::IntoIter {
        LibArchiveIterator { archive: self }
    }
}

#[derive(Debug)]
struct LibArchiveIterator {
    archive: LibArchiveWrapper,
}

impl Iterator for LibArchiveIterator {
    type Item = ArchiveResult<ArchiveFile>;

    fn next(&mut self) -> Option<Self::Item> {
        let file_pos = self.archive.current_pos() as usize;

        self.archive
            .next_entry()
            .transpose()?
            .map(|entry| (entry.file_type(), entry.file_path().to_owned()))
            .map_err(Into::into)
            .and_then(|(file_type, file_path)| {
                build_archive_file(&self.archive, file_pos, file_type, file_path)
            })
            .into()
    }
}
