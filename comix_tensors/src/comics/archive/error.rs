use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};
use std::io::Error as IoError;

use super::{MagicType, MagicTypeError};

pub trait ComicContainerError: Error + Send + Sync {}

///Errors returned while trying to init ComicContainer
#[derive(Debug)]
pub enum InitComicContainerError {
    Magic(MagicTypeError),
    UnsupportedType(MagicType),
}

impl From<MagicTypeError> for InitComicContainerError {
    fn from(err: MagicTypeError) -> Self {
        InitComicContainerError::Magic(err)
    }
}

impl Display for InitComicContainerError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        use self::InitComicContainerError::*;

        match self {
            Magic(_) => writeln!(f, "Can't open file as comic container. Error occurred while trying to determine magic type"),
            UnsupportedType(magic_type) => writeln!(f, "File type '{:?}' is currently can't be opened as comic container", magic_type),
        }
    }
}

impl Error for InitComicContainerError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        match self {
            InitComicContainerError::Magic(e) => Some(e),
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
