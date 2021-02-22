use std::fmt::{Display, Formatter, Result as FmtResult};

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Debug)]
pub enum Error {
    IO(std::io::Error),
    Path(PathErrorKind, &'static str),
    Sys,
}

impl Display for Error {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        match self {
            Self::IO(err) => Display::fmt(err, f),
            Self::Path(kind, msg) => writeln!(f, "{}: {}", msg, kind),
            Self::Sys => writeln!(f, "Tensorflow Lite inner error occur"),
        }
    }
}

impl std::error::Error for Error {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            Self::IO(err) => Some(err),
            _ => None,
        }
    }
}

impl From<std::io::Error> for Error {
    fn from(err: std::io::Error) -> Self {
        Error::IO(err)
    }
}

#[derive(Debug)]
pub enum PathErrorKind {
    NotExists,
    Invalid,
}

impl Display for PathErrorKind {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        match self {
            Self::NotExists => writeln!(f, "Provided file path doesn't exist"),
            Self::Invalid => writeln!(f, "Provided file path has invalid symbols"),
        }
    }
}
