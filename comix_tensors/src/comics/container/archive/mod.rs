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
