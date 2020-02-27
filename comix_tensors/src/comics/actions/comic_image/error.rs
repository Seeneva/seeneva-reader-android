use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};

use image::ImageError;

#[derive(Debug)]
pub enum GetComicImageError {
    ///Cant find image in the comic book container by it position
    CantFind,
    ///Error occured when tried to open image
    OpenError(ImageError),
    ///File does not have any content
    EmptyFile,
}

impl Display for GetComicImageError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        use self::GetComicImageError::*;

        match self {
            CantFind => writeln!(f, "Can't find comic image by position"),
            OpenError(_) => writeln!(f, "Can't open comic image by position"),
            EmptyFile => writeln!(
                f,
                "Can't open comic image by position. It doesn't have any content"
            ),
        }
    }
}

impl Error for GetComicImageError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        use self::GetComicImageError::*;

        match self {
            OpenError(e) => Some(e),
            _ => None,
        }
    }
}
