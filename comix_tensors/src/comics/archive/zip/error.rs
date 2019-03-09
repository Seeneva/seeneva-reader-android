use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};
use std::io::Error as IoError;
use zip::result::ZipError as ZipErrorLib;

#[derive(Debug)]
pub struct ZipError(ZipErrorLib);

impl From<ZipError> for Box<dyn Error + Send> {
    fn from(err: ZipError) -> Self {
        Box::new(err)
    }
}

impl From<ZipErrorLib> for ZipError {
    fn from(err: ZipErrorLib) -> Self {
        ZipError(err)
    }
}

impl From<IoError> for ZipError {
    fn from(err: IoError) -> Self {
        ZipError(ZipErrorLib::Io(err))
    }
}

impl Display for ZipError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        self.0.fmt(f)
    }
}

impl Error for ZipError {}
