use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};

use libc::c_int;

pub type ArchiveResult<T> = Result<T, ArchiveError>;

#[derive(Debug, Clone)]
pub enum ArchiveError {
    Null,
    Sys(c_int, String),
}

impl Display for ArchiveError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        fn fmt_inner<T: Display>(f: &mut Formatter, t: T) -> FmtResult {
            writeln!(f, "Archive error occurred: '{}'", t)
        }

        match self {
            ArchiveError::Null => fmt_inner(f, "Cannot allocate object"),
            ArchiveError::Sys(code, msg) => fmt_inner(
                f,
                format!("Libarchive archive error. Code: {}. Message: {}", code, msg),
            ),
        }
    }
}

impl Error for ArchiveError {}
