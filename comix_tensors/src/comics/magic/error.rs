use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};
use std::io::Error as IOError;

#[derive(Debug)]
pub enum MagicTypeError {
    IO(IOError),
    NotFile,
    UnknownFormat,
}

impl From<IOError> for MagicTypeError {
    fn from(error: IOError) -> Self {
        MagicTypeError::IO(error)
    }
}

impl Display for MagicTypeError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        use self::MagicTypeError::*;

        match self {
            IO(ref e) => writeln!(f, "Can't open file. IO error occurred. =>>> {}", e),
            NotFile => writeln!(f, "Can't open as a File"),
            UnknownFormat => writeln!(f, "Can't resolve file format. Unknown file's magic numbers"),
        }
    }
}

impl Error for MagicTypeError {}
