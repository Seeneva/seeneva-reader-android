use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};

use image::ImageError;

///Describe errors during comic files preprocessing
#[derive(Debug)]
pub struct ComicPreprocessError(ImageError);

impl From<ImageError> for ComicPreprocessError {
    fn from(err: ImageError) -> Self {
        ComicPreprocessError(err)
    }
}

impl Display for ComicPreprocessError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        Display::fmt(&self.0, f)
    }
}

impl Error for ComicPreprocessError {}
