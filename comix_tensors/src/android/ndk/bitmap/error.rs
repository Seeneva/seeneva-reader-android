use thiserror::Error as DeriveError;

use super::Status;

pub type Result<T> = std::result::Result<T, Error>;

#[derive(DeriveError, Debug)]
pub enum Error {
    #[error("Android bitmap JNI error. Reason: '{0}'")]
    Jni(#[from] jni::errors::Error),
    #[error("Android bitmap inner error. Reason: '{0:?}'")]
    Sys(Status),
}

impl From<Status> for Error {
    fn from(status: Status) -> Self {
        Error::Sys(status)
    }
}
