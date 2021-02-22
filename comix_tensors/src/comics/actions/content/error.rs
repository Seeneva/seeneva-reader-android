use crate::task::error::CancelledError;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum GetComicBookContentError {
    #[error("Provided comic book container doesn't have any supported page")]
    Empty,
    #[error("{0}")]
    Cancelled(#[from] CancelledError),
}
