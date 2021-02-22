use thiserror::Error as DeriveError;

use crate::comics::container::ComicContainerError;
use crate::task::CancelledError;

#[derive(DeriveError, Debug)]
pub enum GetComicRawImageError {
    ///Error occurred while trying to find raw comic book page
    #[error("Can't get comic book raw page. Container error: '{0}'")]
    ContainerError(#[from] Box<dyn ComicContainerError>),
    ///Error occurred when tried to open image
    #[error("Can't open comic book page as image. Error: '{0}'")]
    ImgError(#[from] image::ImageError),
    /// Task was cancelled
    #[error("{0}")]
    Cancelled(#[from] CancelledError),
}

#[derive(DeriveError, Debug, Copy, Clone)]
#[error("{0}")]
pub struct ResizeComicImageError(#[from] pub CancelledError);
