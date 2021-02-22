use thiserror::Error as DeriveError;

///Errors returned while trying to init ComicContainer
#[derive(DeriveError, Debug)]
pub enum InitComicContainerError {
    /// Returned in case of unsupported file
    #[error("Cannot be opened as comic book container")]
    Unsupported,
    #[error("Can't open file as comic book container. IO error: '{0}'")]
    IO(#[from] std::io::Error),
    #[error("Can't open file as comic book container. Container error: '{0}'")]
    ContainerError(#[from] Box<dyn ComicContainerError>),
}

impl<E: ComicContainerError + 'static> From<E> for InitComicContainerError {
    fn from(container_error: E) -> Self {
        Self::ContainerError(Box::new(container_error))
    }
}

///Base trait for comic book containers errors
pub trait ComicContainerError:
    std::error::Error + thiserror::private::AsDynError + Send + Sync
{
}
