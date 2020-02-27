use std::fmt::{Display, Formatter, Result as FmtResult};

use lopdf::Error as PdfError;

use super::ComicContainerError;

#[derive(Debug)]
pub enum Error {
    Inner(PdfError),
    NotSupported,
}

impl From<PdfError> for Error {
    fn from(inner: PdfError) -> Self {
        Error::Inner(inner)
    }
}

impl ComicContainerError for Error {}

impl From<Error> for Box<dyn ComicContainerError> {
    fn from(err: Error) -> Self {
        Box::new(err)
    }
}

impl From<std::io::Error> for Error {
    fn from(inner: std::io::Error) -> Self {
        PdfError::IO(inner).into()
    }
}

impl Display for Error {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        use Error::*;

        match self {
            Inner(e) => Display::fmt(e, f),
            NotSupported => writeln!(f, "The PDF file cannot be read"),
        }
    }
}

impl std::error::Error for Error {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        use Error::*;

        match self {
            Inner(e) => Some(e),
            _ => None,
        }
    }
}
