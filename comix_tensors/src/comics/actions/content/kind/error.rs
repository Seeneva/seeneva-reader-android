use thiserror::Error as DeriveError;

pub type Result<T> = std::result::Result<T, Error>;

///Errors occurred while trying to get comic file metadata
#[derive(DeriveError, Debug)]
pub enum Error {
    ///Error occurred than can't open image file in the container
    #[error("Can't open comic book image file while trying to get it metadata: '{0}'")]
    CantOpenImage(#[from] image::ImageError),
    #[error("Library doesn't support this comic book container file")]
    NotSupported,
}
