use super::{MagicType, MagicTypeError};
use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};

///Errors returned while trying to init ComicContainer
#[derive(Debug)]
pub enum ComicContainerError {
    Magic(MagicTypeError),
    UnsupportedType(MagicType),
}

impl From<MagicTypeError> for ComicContainerError {
    fn from(err: MagicTypeError) -> Self {
        ComicContainerError::Magic(err)
    }
}

impl Display for ComicContainerError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        use self::ComicContainerError::*;

        match self {
            Magic(ref e) => writeln!(f, "Can't open file as comic container. Error occurred while trying to determine magic type. =>>> {}", e),
            UnsupportedType(ref magic_type) => writeln!(f, "File type '{:?}' is currently can't be opened as comic container", magic_type),
        }
    }
}

impl Error for ComicContainerError {}
