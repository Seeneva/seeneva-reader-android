use std::fmt::{Display, Formatter, Result as FmtResult};
use thiserror::Error as DeriveError;

pub type Result<T> = std::result::Result<T, Error>;

#[derive(DeriveError, Debug, Clone)]
#[error("Android assets inner error. Reason: {0}. Comment: {1:?}")]
pub struct Error(SysErrorKind, Option<String>);

impl Error {
    pub(super) fn new_sys_error(kind: SysErrorKind, comment: Option<impl Into<String>>) -> Self {
        Error(kind, comment.map(Into::into))
    }
}

#[derive(Debug, Copy, Clone)]
pub enum SysErrorKind {
    Null,
}

impl Display for SysErrorKind {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        match self {
            Self::Null => writeln!(f, "Null pointer"),
        }
    }
}
