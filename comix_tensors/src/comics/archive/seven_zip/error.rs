use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};
use std::io::Error as IoError;
use std::string::FromUtf16Error;

use super::{ComicContainerError, SZ};

#[derive(Debug)]
pub enum SevenZipError {
    //Any errors, which LZMA library return
    Native(SZ),
    IO(IoError),
    NameError(FromUtf16Error),
}

impl ComicContainerError for SevenZipError {}

impl From<SevenZipError> for Box<dyn ComicContainerError> {
    fn from(err: SevenZipError) -> Self {
        Box::new(err)
    }
}

impl From<SZ> for SevenZipError {
    fn from(inner: SZ) -> Self {
        SevenZipError::Native(inner)
    }
}

impl From<IoError> for SevenZipError {
    fn from(io_err: IoError) -> Self {
        SevenZipError::IO(io_err)
    }
}

impl From<FromUtf16Error> for SevenZipError {
    fn from(err: FromUtf16Error) -> Self {
        SevenZipError::NameError(err)
    }
}

impl Display for SevenZipError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        let txt = "7z error occurred while processing archive";

        match self {
            SevenZipError::Native(sz) => writeln!(f, "{}. Inner error: {:?}", txt, sz),
            SevenZipError::IO(err) => writeln!(f, "{}. IO error: {}", txt, err),
            SevenZipError::NameError(err) => {
                writeln!(f, "{}. Can't get file name error: {}", txt, err)
            }
        }
    }
}

impl Error for SevenZipError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        match self {
            SevenZipError::IO(err) => Some(err),
            SevenZipError::NameError(err) => Some(err),
            _ => None,
        }
    }
}
