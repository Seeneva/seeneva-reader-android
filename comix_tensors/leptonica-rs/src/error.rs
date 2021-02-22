use std::io;

use std::fmt::{Display, Formatter, Result as FmtResult};
use thiserror::Error as DeriveError;

pub type Result<T> = std::result::Result<T, Error>;

#[derive(DeriveError, Debug)]
pub enum Error {
    #[error("IO error occurred: {0}")]
    IO(#[from] io::Error),
    #[error("Leptonica inner error. Reason: {0}. Comment: {1:?}")]
    Sys(SysErrorKind, Option<String>),
}

impl Error {
    /// Create new sys error with optional comment
    pub fn new_sys_error(kind: SysErrorKind, comment: Option<impl Into<String>>) -> Self {
        Self::Sys(kind, comment.map(Into::into))
    }
}

#[derive(Debug, Copy, Clone)]
pub enum SysErrorKind {
    Null,
    NotOk,
    InvalidBPP,
}

impl Display for SysErrorKind {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        match self {
            Self::Null => writeln!(f, "Leptonica return null pointer"),
            Self::NotOk => writeln!(f, "Leptonica return not success result"),
            Self::InvalidBPP => writeln!(f, "Leptonica supports 1, 2, 3, 4, 8 and 32 bpp only"),
        }
    }
}
