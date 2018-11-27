use crate::futures_ext::cancel::{CancelSignalError, Cancelled};
use crate::{ComicContainerError, ComicPreprocessError};

use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};

use jni::errors::Error as JniError;
use tokio::prelude::future::SharedError;

///Error whic describe all error variants during comic container files preprocessing
#[derive(Debug)]
pub enum ProcessError {
    ///Any JNI error
    Jni(JniError),
    ///Error occurred while reading comic container file
    ContainerRead(Box<Error + Send + Sync>),
    ///Error occurred
    ContainerOpen(ComicContainerError),
    ///Errors durrng preprocessing comics files
    Preprocess(ComicPreprocessError),
    ///Error during cancelation
    CancelledError(SharedError<CancelSignalError>),
    ///The task has been cancelled
    Cancelled,
}

impl From<ComicPreprocessError> for ProcessError {
    fn from(err: ComicPreprocessError) -> Self {
        ProcessError::Preprocess(err)
    }
}

impl From<Cancelled> for ProcessError {
    fn from(_: Cancelled) -> Self {
        ProcessError::Cancelled
    }
}

impl From<SharedError<CancelSignalError>> for ProcessError {
    fn from(err: SharedError<CancelSignalError>) -> Self {
        ProcessError::CancelledError(err)
    }
}

impl From<ComicContainerError> for ProcessError {
    fn from(err: ComicContainerError) -> Self {
        ProcessError::ContainerOpen(err)
    }
}

impl From<Box<Error + Send + Sync>> for ProcessError {
    fn from(err: Box<Error + Send + Sync>) -> Self {
        ProcessError::ContainerRead(err)
    }
}

impl From<JniError> for ProcessError {
    fn from(err: JniError) -> Self {
        ProcessError::Jni(err)
    }
}

impl Display for ProcessError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        use self::ProcessError::*;

        match self {
            Jni(e) => writeln!(
                f,
                "JNI error occurred during comic files processing. =>>> {}",
                e
            ),
            ContainerRead(e) => writeln!(
                f,
                "Error occurred while trying to read files from comics archive. =>>> {}",
                e
            ),
            ContainerOpen(e) => writeln!(
                f,
                "Can't open comic archive file for processing files. =>>> {}",
                e
            ),
            Cancelled => writeln!(f, "Comic files processing was cancelled"),
            CancelledError(e) => writeln!(f, "Can't cancel comic files processing. =>>> {}", e),
            Preprocess(e) => writeln!(f, "Can't preprocess comic files. =>>> {}", e),
        }
    }
}

impl Error for ProcessError {}
