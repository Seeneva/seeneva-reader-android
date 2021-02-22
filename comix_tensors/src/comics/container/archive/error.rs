use thiserror::Error as DeriveError;

use libarchive_rs::FileType;

use super::ComicContainerError;

pub type Result<T> = std::result::Result<T, Error>;

#[derive(DeriveError, Debug)]
pub enum Error {
    #[error("Inner libarchive error occurred during archive reading: {0}")]
    Inner(#[from] libarchive_rs::ArchiveError),
    #[error("IO error occurred during archive reading: {0}")]
    IO(#[from] std::io::Error),
    #[error("Archive entry is not a file. It is: '{0:?}'")]
    NotAFile(FileType),
}

impl ComicContainerError for Error {}

impl From<Error> for Box<dyn ComicContainerError> {
    fn from(err: Error) -> Self {
        Box::new(err)
    }
}
