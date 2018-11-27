use super::SZ;
use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};
use std::string::FromUtf16Error;

#[derive(Debug, Clone)]
pub enum SevenZipError {
    //Any errors, which LZMA library return
    Native(SZ),
    //Other errors without specific codes
    Other(String),
}

impl From<SevenZipError> for Box<dyn Error + Send> {
    fn from(err: SevenZipError) -> Self {
        Box::new(err)
    }
}

impl From<String> for SevenZipError {
    fn from(error_txt: String) -> Self {
        SevenZipError::Other(error_txt)
    }
}

impl From<FromUtf16Error> for SevenZipError {
    fn from(_: FromUtf16Error) -> Self {
        "Can't convert UTF16 string to UTF8".to_string().into()
    }
}

impl Display for SevenZipError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        use self::SevenZipError::*;

        match *self {
            Native(ref sz) => writeln!(
                f,
                "7z error occurred while processing archive. Error: {:?}",
                sz
            ),
            Other(ref txt) => writeln!(
                f,
                "Non 'system' 7z error occurred while processing archive. Error: {}",
                txt
            ),
        }
    }
}

impl Error for SevenZipError {}
