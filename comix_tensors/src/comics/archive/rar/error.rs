use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};
use std::io::Error as IoError;

use super::{ComicContainerError, OpenResult};

#[derive(Debug)]
pub enum RarError {
    Inner(OpenResult),
    IO(IoError),
}

impl ComicContainerError for RarError {}

impl From<RarError> for Box<dyn ComicContainerError> {
    fn from(err: RarError) -> Self {
        Box::new(err)
    }
}

impl From<OpenResult> for RarError {
    fn from(open_result: OpenResult) -> Self {
        assert_eq!(open_result.is_ok(), false);

        RarError::Inner(open_result)
    }
}

impl From<IoError> for RarError {
    fn from(err: IoError) -> Self {
        RarError::IO(err)
    }
}

impl Display for RarError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        let txt = "RAR error occurred while processing archive";

        match self {
            RarError::Inner(open_result) => {
                writeln!(f, "{}. Inner RAR error {:?}", txt, open_result)
            }
            RarError::IO(io_error) => writeln!(f, "{}. IO error {}", txt, io_error),
        }
    }
}

impl Error for RarError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        match self {
            RarError::IO(io_error) => Some(io_error),
            _ => None,
        }
    }
}
