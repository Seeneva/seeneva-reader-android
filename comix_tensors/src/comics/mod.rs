mod actions;
mod archive;
mod magic;

use self::actions::*;
use self::archive::ArchiveFile;
use crate::InterpreterInput;

use tokio::prelude::Stream;

#[derive(Debug, Clone, Default)]
pub struct ComicInfo {
    pub title: Option<String>,
    pub series: Option<String>,
    pub summary: Option<String>,
    pub number: Option<u32>,
    pub count: Option<u32>,
    pub year: Option<u16>,
    pub month: Option<u8>,
    pub writer: Option<String>,
    pub publisher: Option<String>,
    pub penciller: Option<String>,
    pub cover_artist: Option<String>,
    pub genre: Option<String>,
    pub black_and_white: bool,
    pub manga: bool,
    pub characters: Option<String>,
    pub page_count: Option<u32>,
    pub web: Option<String>,
    pub notes: Option<String>,
    pub volume: Option<String>,
    pub language_iso: Option<String>,
    pub pages: Option<Vec<ComicInfoPage>>,
}

/// Metadata of a single comic page
#[derive(Debug, Clone, Default)]
pub struct ComicInfoPage {
    pub image: Option<u32>,
    pub image_type: Option<String>,
    pub image_size: Option<usize>,
    pub image_width: Option<u32>,
    pub image_height: Option<u32>,
}

///Describes single comic page data
#[derive(Debug, Clone)]
pub struct ComicPageContent {
    ///position in the archive.
    pub pos: usize,
    pub name: String,
    pub content: Vec<u8>,
}

impl From<ArchiveFile> for ComicPageContent {
    fn from(file: ArchiveFile) -> Self {
        ComicPageContent {
            pos: file.pos,
            name: file.name,
            content: file
                .content
                .expect("Comic page content should have buffer!"),
        }
    }
}

/// All possible variants of comic files
#[derive(Debug, Clone)]
pub enum ComicFile {
    ///Image file as the TF model input
    ComicPage(ComicPageContent),
    ///Comic parsed metadata
    ComicInfo(ComicInfo),
}

impl From<ComicInfo> for ComicFile {
    fn from(comic_info: ComicInfo) -> Self {
        ComicFile::ComicInfo(comic_info)
    }
}

impl From<ComicPageContent> for ComicFile {
    fn from(page_content: ComicPageContent) -> Self {
        ComicFile::ComicPage(page_content)
    }
}

pub trait ComicContainerActions: Stream<Item = ArchiveFile> {
    ///Filter stream and return all supported comic files, founded in the container
    fn get_comic_files<E>(self) -> FilterComicFilesStream<Self>
    where
        Self: Sized + Stream<Error = E>,
        E: From<NoComicPages>,
    {
        FilterComicFilesStream::new(self)
    }
}

impl<T> ComicContainerActions for T where T: Stream<Item = ArchiveFile> {}

pub mod prelude {
    pub use super::actions::NoComicPages;
    pub use super::archive::{ComicContainerError, ComicContainerVariant};
    pub use super::magic::MagicTypeError;
    //pub use super::preprocessor::{ComicPreprocessError, ComicPreprocessing};
    pub use super::{ComicContainerActions, ComicFile, ComicInfo, ComicInfoPage, ComicPageContent};
}
