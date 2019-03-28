use crate::futures_ext::cancel::{CancelSignalError, Cancelled};
use crate::{ComicContainerError, NoComicPages};

use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};

use jni::errors::Error as JniError;
use tokio::prelude::future::SharedError;

///Error which describe all error variants during comic book opening
#[derive(Debug)]
pub enum ComicBookOpenError {
    ///Any JNI error
    Jni(JniError),
    ///Error occurred while reading comic container file
    ContainerRead(Box<Error + Send + Sync>),
    ///Error occurred while trying to open container file
    ContainerOpen(ComicContainerError),
    ///Error occurred if after files filtering there is no images
    Empty(NoComicPages),
    ///Error during cancelation
    CancelledError(SharedError<CancelSignalError>),
    ///The task has been cancelled
    Cancelled,
}

impl From<Cancelled> for ComicBookOpenError {
    fn from(_: Cancelled) -> Self {
        ComicBookOpenError::Cancelled
    }
}

impl From<NoComicPages> for ComicBookOpenError {
    fn from(err: NoComicPages) -> Self {
        ComicBookOpenError::Empty(err)
    }
}

impl From<SharedError<CancelSignalError>> for ComicBookOpenError {
    fn from(err: SharedError<CancelSignalError>) -> Self {
        ComicBookOpenError::CancelledError(err)
    }
}

impl From<ComicContainerError> for ComicBookOpenError {
    fn from(err: ComicContainerError) -> Self {
        ComicBookOpenError::ContainerOpen(err)
    }
}

impl From<Box<Error + Send + Sync>> for ComicBookOpenError {
    fn from(err: Box<Error + Send + Sync>) -> Self {
        ComicBookOpenError::ContainerRead(err)
    }
}

impl From<JniError> for ComicBookOpenError {
    fn from(err: JniError) -> Self {
        ComicBookOpenError::Jni(err)
    }
}

impl Display for ComicBookOpenError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        use self::ComicBookOpenError::*;

        match self {
            Jni(e) => writeln!(
                f,
                "JNI error occurred during comic book opening. =>>> {}",
                e
            ),
            ContainerRead(e) => writeln!(
                f,
                "Error occurred while trying to read files from comics archive. =>>> {}",
                e
            ),
            ContainerOpen(e) => writeln!(
                f,
                "Can't open comic container file for reading comic book files. =>>> {}",
                e
            ),
            Cancelled => writeln!(f, "Comic book opening was cancelled"),
            CancelledError(e) => writeln!(f, "Can't cancel comic book opening. =>>> {}", e),
            Empty(e) => Display::fmt(e, f),
        }
    }
}

impl Error for ComicBookOpenError {}
