use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};
use std::io::Error as IoError;

use libarchive_rs::error::ArchiveError as ArchiveErrorLib;

use crate::comics::container::ComicContainerError;

#[derive(Debug)]
pub enum ArchiveError {
    Inner(ArchiveErrorLib),
    IO(IoError),
}

impl Display for ArchiveError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        let e: &dyn Error = match self {
            ArchiveError::Inner(e) => e,
            ArchiveError::IO(e) => e,
        };

        Display::fmt(e, f)
    }
}

impl Error for ArchiveError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        let e: &dyn Error = match self {
            ArchiveError::Inner(e) => e,
            ArchiveError::IO(e) => e,
        };

        Some(e)
    }
}

impl ComicContainerError for ArchiveError {}

impl From<ArchiveErrorLib> for ArchiveError {
    fn from(err: ArchiveErrorLib) -> Self {
        ArchiveError::Inner(err)
    }
}

impl From<IoError> for ArchiveError {
    fn from(err: IoError) -> Self {
        ArchiveError::IO(err)
    }
}

impl From<ArchiveError> for Box<dyn ComicContainerError> {
    fn from(err: ArchiveError) -> Self {
        Box::new(err)
    }
}
