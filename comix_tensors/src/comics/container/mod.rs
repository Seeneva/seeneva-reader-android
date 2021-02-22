use enum_dispatch::enum_dispatch;

use file_descriptor::FileRawFd;

use super::magic::MagicType;

pub use self::error::*;

mod archive;
mod error;
mod pdf;

/// Comic book page
/// Files usually not sorted by name in a container. So use [pos] to get real file position
#[derive(Debug, Clone)]
pub struct ComicContainerFile {
    ///position in the archive. 0 based
    pub pos: usize,
    ///file name
    pub name: String,
    /// File content
    pub content: Vec<u8>,
}

type FilesIter<'a> =
    Box<dyn Iterator<Item = Result<ComicContainerFile, Box<dyn ComicContainerError>>> + Send + 'a>;

type FindFileResult = Result<Option<ComicContainerFile>, Box<dyn ComicContainerError>>;

///Base trait for all supported comics container
#[enum_dispatch]
pub trait ComicContainerVariant {
    ///Iterate all files in the comic container
    fn files(&mut self) -> FilesIter;

    /// Get single comic container file by provided [pos]. Position is 0 based.
    fn file_at(&mut self, pos: usize) -> FindFileResult;
}

///Comic book container
#[enum_dispatch(ComicContainerVariant)]
#[derive(Debug)]
pub enum ComicContainer {
    /// Archive comic book container
    Archive(archive::ArchiveComicContainer),
    /// PDF comic book container
    PDF(pdf::PDFComicContainer),
}

impl ComicContainer {
    /// Trying to open provided file descriptor as comic book container
    #[cfg(target_family = "unix")]
    pub fn open_fd(fd: impl Into<FileRawFd>) -> Result<Self, InitComicContainerError> {
        let mut fd = fd.into();

        MagicType::guess_reader_type(&mut fd)
            .map_err(Into::into)
            .and_then(|file_magic| match file_magic {
                Some(MagicType::PDF) => {
                    debug!("Trying to open {:?} as PDF", fd);
                    Ok(pdf::PDFComicContainer::open(fd)?.into())
                }
                Some(file_magic) => {
                    error!("Unsupported comic book container: '{:?}'", file_magic);
                    Err(InitComicContainerError::Unsupported)
                }
                None => {
                    debug!("Trying to open {:?} as archive", fd);
                    Ok(archive::ArchiveComicContainer::open(fd.dup()?)?.into())
                }
            })
    }
}

#[cfg(test)]
pub mod tests {
    use std::env;
    use std::fs::File;
    use std::os::unix::io::IntoRawFd;

    pub use crate::tests::base_test_path;

    use super::*;

    #[cfg(target_family = "unix")]
    pub fn open_archive_fd(archive_path: &[&str]) -> FileRawFd {
        let mut path = base_test_path();
        path.push("archive");
        path.extend(archive_path);

        println!("Trying to open file {:?}", path);

        File::open(path).unwrap().into()
    }
}
