use thiserror::Error as DeriveError;

use super::ComicContainerError;

/// PDF comic book container result
pub type Result<T> = std::result::Result<T, Error>;

/// PDF comic book container error
#[derive(DeriveError, Debug)]
pub enum Error {
    #[error("lopdf inner error: '{0}'")]
    Inner(#[from] lopdf::Error),
    #[error("The PDF file cannot be read")]
    NotSupported,
}

impl ComicContainerError for Error {}

impl From<Error> for Box<dyn ComicContainerError> {
    fn from(err: Error) -> Self {
        Box::new(err)
    }
}
