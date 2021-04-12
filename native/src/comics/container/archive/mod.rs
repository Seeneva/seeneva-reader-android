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

use std::io::Read;

use libarchive_rs::{
    Archive, ArchiveError, Builder as ArchiveBuilder, Entry, FileType, Format,
    Source as ArchiveSource, Status as ArchiveStatus, SysErrorKind as ArchiveSysErrorKind,
};

use super::{
    ComicContainerError, ComicContainerFile, ComicContainerVariant, FilesIter, FindFileResult,
};

pub use self::error::*;

mod error;

/// Return base comic book archive reader builder
fn base_comic_archive_builder() -> ArchiveBuilder {
    // Comic book historical formats: .cbz (zip), .cbr (rar), .cb7 (7z), .cbt (tar)
    Archive::builder()
        .add_format(Format::Zip)
        .add_format(Format::Tar)
        .add_format(Format::SevenZip)
        .add_format(Format::Rar)
        .add_format(Format::Rar5)
}

/// Build archive file from parts
fn build_comic_container_file(archive: &mut Archive, entry: &Entry) -> Result<ComicContainerFile> {
    let e_pos = archive.current_pos();
    let e_path = entry.path();
    let e_type = entry.file_type();

    debug!(
        "File extracted. Pos: '{}'. Path: '{}'. Type: '{:?}'",
        e_pos, e_path, e_type
    );

    // ignore non file entries
    let content = match e_type {
        FileType::RegularFile => {
            let mut buf = Vec::with_capacity(entry.file_size());

            //ignore errors
            archive.read_to_end(&mut buf)?;

            buf
        }
        _ => {
            return Err(Error::NotAFile(e_type));
        }
    };

    Ok(ComicContainerFile {
        pos: e_pos,
        name: e_path.to_string(),
        content,
    })
}

/// Archive comic book container which filter only image pages
#[derive(Debug)]
pub struct ArchiveComicContainer(Archive);

impl ArchiveComicContainer {
    /// Create new comic book container from provided [archive]
    fn new(archive: Archive) -> Self {
        ArchiveComicContainer(archive)
    }

    /// Open comic book from provided archive source
    pub fn open(source: impl Into<ArchiveSource>) -> Result<Self> {
        base_comic_archive_builder()
            .read(source)
            .map(Into::into)
            .map_err(Into::into)
    }
}

impl From<Archive> for ArchiveComicContainer {
    fn from(archive: Archive) -> Self {
        Self::new(archive)
    }
}

impl ComicContainerVariant for ArchiveComicContainer {
    fn files(&mut self) -> FilesIter {
        Box::new(
            ArchiveComicIterator {
                archive: self,
                entry: None,
            }
            .map(|result| result.map_err(Into::into)),
        )
    }

    fn file_at(&mut self, pos: usize) -> FindFileResult {
        if let Some(entry) = self.0.by_position(pos).map_err(Error::from)? {
            build_comic_container_file(&mut self.0, &entry)
                .map(Some)
                .map_err(Into::into)
        } else {
            Ok(None)
        }
    }
}

/// Iterator over all comic book pages
#[derive(Debug)]
struct ArchiveComicIterator<'a> {
    archive: &'a mut ArchiveComicContainer,
    entry: Option<Entry>,
}

impl Iterator for ArchiveComicIterator<'_> {
    type Item = Result<ComicContainerFile>;

    fn next(&mut self) -> Option<Self::Item> {
        let entry = if let Some(entry) = self.entry.as_mut() {
            entry
        } else {
            match Entry::new(Some(&self.archive.0)) {
                Ok(entry) => self.entry.get_or_insert(entry),
                Err(e) => {
                    error!("Can't init libarchive entry: '{}'", e);
                    return None;
                }
            }
        };

        loop {
            match self.archive.0.next_entry_into(entry).transpose()? {
                Ok(_) => match build_comic_container_file(&mut self.archive.0, entry) {
                    Err(Error::NotAFile(_)) => {
                        //silently ignore any non file entries
                        continue;
                    }
                    result => {
                        return Some(result);
                    }
                },
                Err(ArchiveError::Sys(
                    ArchiveSysErrorKind::ArchiveStatus(ArchiveStatus::Fatal),
                    _,
                )) => {
                    // something really bad happened. It is better to return from the iterator
                    return None;
                }
                Err(e) => {
                    return Some(Err(e.into()));
                }
            };
        }
    }
}

#[cfg(test)]
mod test {
    use std::path::PathBuf;

    use crate::comics::container::tests::{base_archive_path, open_archive_fd};

    use super::*;

    const CBZ: &str = "zip/comics_test.cbz";
    const CB7: &str = "7z/comics_test.cb7";
    const CBR: &str = "rar/comics_test.cbr";

    #[test]
    fn test_zip() {
        base_test(base_archive_path().join(CBZ));
    }

    #[test]
    #[cfg(target_family = "unix")]
    fn test_zip_fd() {
        base_test(open_archive_fd(CBZ));
    }

    #[test]
    fn test_7z() {
        base_test(base_archive_path().join(CB7));
    }

    #[test]
    #[cfg(target_family = "unix")]
    fn test_7z_fd() {
        base_test(open_archive_fd(CB7));
    }

    #[test]
    fn test_rar() {
        base_test(base_archive_path().join(CBR));
    }

    #[test]
    #[cfg(target_family = "unix")]
    fn test_rar_fd() {
        base_test(open_archive_fd(CBR));
    }

    fn base_test(source: impl Into<ArchiveSource>) {
        let files = ArchiveComicContainer::open(source)
            .unwrap()
            .files()
            .filter_map(|file| file.ok())
            .collect::<Vec<_>>();

        assert_eq!(files.len(), 5, "Invalid archive files count");

        let img_count = files
            .into_iter()
            .map(|f| image::guess_format(f.content.as_slice()))
            .filter_map(image::ImageResult::ok)
            .count();

        assert_eq!(img_count, 3, "Invalid archive images count")
    }
}
