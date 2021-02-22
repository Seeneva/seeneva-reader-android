use std::fmt::{Display, Formatter, Result as FmtResult};

use thiserror::Error as DeriveError;

use crate::archive::Status;

pub type ArchiveResult<T> = Result<T, ArchiveError>;

#[derive(DeriveError, Debug)]
pub enum ArchiveError {
    #[error("IO error occurred: {0}")]
    IO(#[from] std::io::Error),
    #[error("Libarchive inner error. Reason: {0}. Comment: {1:?}")]
    Sys(SysErrorKind, Option<String>),
}

impl ArchiveError {
    /// Create new sys error with optional comment
    pub fn new_sys_error(kind: SysErrorKind, comment: Option<impl Into<String>>) -> Self {
        Self::Sys(kind, comment.map(Into::into))
    }
}

/// Describes Libarchive inner errors
#[derive(Debug, Copy, Clone)]
pub enum SysErrorKind {
    Null,
    ArchiveStatus(Status),
}

impl Display for SysErrorKind {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        match self {
            Self::Null => writeln!(f, "Null pointer"),
            Self::ArchiveStatus(status) => writeln!(f, "Status: {:?}", status),
        }
    }
}
