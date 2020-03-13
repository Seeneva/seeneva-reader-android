use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};
use std::io::Error as IoError;

pub trait ComicContainerError: Error + Send + Sync {}

///Errors returned while trying to init ComicContainer
#[derive(Debug)]
pub enum InitComicContainerError {
    /// Returned in case of unsupported file
    Unsupported,
    IO(IoError),
}

impl From<IoError> for InitComicContainerError {
    fn from(err: IoError) -> Self {
        InitComicContainerError::IO(err)
    }
}

impl Display for InitComicContainerError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        use self::InitComicContainerError::*;

        match self {
            IO(e) => writeln!(
                f,
                "Can't open file as comic book container. IO error: '{}'",
                e
            ),
            Unsupported => writeln!(f, "Cannot be opened as comic book container"),
        }
    }
}

impl Error for InitComicContainerError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        match self {
            InitComicContainerError::IO(e) => Some(e),
            _ => None,
        }
    }
}

///Calculate comic book archive hash error
#[derive(Debug)]
pub struct CalcArchiveHashError(IoError);

impl From<IoError> for CalcArchiveHashError {
    fn from(inner: IoError) -> Self {
        CalcArchiveHashError(inner)
    }
}

impl Display for CalcArchiveHashError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        writeln!(f, "Can't calculate comic book archive hash")
    }
}

impl Error for CalcArchiveHashError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        Some(&self.0)
    }
}
