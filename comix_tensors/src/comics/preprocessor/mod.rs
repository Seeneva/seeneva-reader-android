mod comic_files;
mod error;

pub use self::error::ComicPreprocessError;

use self::comic_files::PreprocessComicFileStream;
use super::archive::ArchiveFile;
use super::magic::MagicType;
use crate::InterpreterInShape;

use tokio::prelude::*;

pub trait ComicPreprocessing: Stream {
    ///Filter and preprocess comic files to TF interpreter input
    /// Also can parse additional metadata if any
    fn preprocess_comic_files<E>(
        self,
        interpreter_input_shape: InterpreterInShape,
    ) -> PreprocessComicFileStream<Self>
    where
        E: From<ComicPreprocessError>,
        Self: Sized + Stream<Item = ArchiveFile, Error = E>,
    {
        PreprocessComicFileStream::new(self, interpreter_input_shape)
    }
}

impl<T> ComicPreprocessing for T where T: Stream<Item = ArchiveFile> {}
