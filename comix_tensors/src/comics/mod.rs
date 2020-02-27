use tokio::prelude::*;

use self::actions::*;
use self::archive::ArchiveFile;

mod actions;
mod archive;
mod magic;

#[derive(Debug, Clone, Default)]
pub struct ComicInfo {
    pub title: Option<String>,
    pub series: Option<String>,
    pub summary: Option<String>,
    pub number: Option<u32>,
    pub count: Option<u32>,
    pub volume: Option<u32>,
    pub page_count: Option<u32>,
    pub year: Option<u16>,
    pub month: Option<u8>,
    pub day: Option<u8>,
    pub publisher: Option<String>,
    pub writer: Option<String>,
    pub penciller: Option<String>,
    pub inker: Option<String>,
    pub colorist: Option<String>,
    pub letterer: Option<String>,
    pub cover_artist: Option<String>,
    pub editor: Option<String>,
    pub imprint: Option<String>,
    pub genre: Option<String>,
    pub format: Option<String>,
    pub age_rating: Option<String>,
    pub teams: Option<String>,
    pub locations: Option<String>,
    pub story_arc: Option<String>,
    pub series_group: Option<String>,
    pub black_and_white: Option<bool>,
    pub manga: Option<bool>,
    pub characters: Option<String>,
    pub web: Option<String>,
    pub notes: Option<String>,
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

impl ComicInfoPage {
    pub fn is_cover(&self) -> bool {
        self.image_type.as_ref().map(String::as_str) == Some("ComicInfoPage")
    }
}

///Describes single comic page data
#[derive(Debug, Clone)]
pub struct ComicPageMetadata {
    ///position in the archive.
    pub pos: usize,
    pub name: String,
    pub width: u32,
    pub height: u32,
}

impl From<(ArchiveFile, u32, u32)> for ComicPageMetadata {
    fn from(source: (ArchiveFile, u32, u32)) -> Self {
        let (file, width, height) = source;

        ComicPageMetadata {
            pos: file.pos,
            name: file.name,
            width,
            height,
        }
    }
}

/// All possible variants of comic files
#[derive(Debug, Clone)]
pub enum ComicBookMetadataType {
    ///Image file as the TF model input
    ComicPage(ComicPageMetadata),
    ///Comic parsed metadata
    ComicInfo(ComicInfo),
}

impl From<ComicInfo> for ComicBookMetadataType {
    fn from(comic_info: ComicInfo) -> Self {
        ComicBookMetadataType::ComicInfo(comic_info)
    }
}

impl From<ComicPageMetadata> for ComicBookMetadataType {
    fn from(page_content: ComicPageMetadata) -> Self {
        ComicBookMetadataType::ComicPage(page_content)
    }
}

pub trait ComicFilesActions: Stream<Item = ArchiveFile> {
    ///Filter stream and return all supported comic files, founded in the container
    fn get_comic_file_metadata<E>(self) -> GetComicFileMetadataStream<Self>
    where
        Self: Sized + Stream<Error = E>,
        E: From<GetComicFileMetadataError>,
    {
        GetComicFileMetadataStream::new(self)
    }
}

pub trait ComicFileActions: Future<Item = Option<ArchiveFile>> {
    ///Return future which returns image's RGBA content if any
    fn extract_image<E>(self) -> GetComicImageFuture<Self>
    where
        Self: Sized + Future<Error = E>,
        E: From<GetComicImageError>,
    {
        GetComicImageFuture::new(self, None)
    }

    fn extract_thumbnail<E>(self, width: u32, height: u32) -> GetComicImageFuture<Self>
    where
        Self: Sized + Future<Error = E>,
        E: From<GetComicImageError>,
    {
        GetComicImageFuture::new(self, Some((width, height)))
    }
}

impl<T> ComicFilesActions for T where T: Stream<Item = ArchiveFile> {}
impl<T> ComicFileActions for T where T: Future<Item = Option<ArchiveFile>> {}

pub mod prelude {
    pub use super::{
        ComicBookMetadataType, ComicFileActions, ComicFilesActions, ComicInfo, ComicInfoPage,
        ComicPageMetadata,
    };
    //pub use super::preprocessor::{ComicPreprocessError, ComicPreprocessing};
    pub use super::actions::{GetComicFileMetadataError, GetComicImageError};
    pub use super::archive::{
        hash_metadata as archive_hash_metadata, CalcArchiveHashError, ComicContainerError,
        ComicContainerVariant, InitComicContainerError,
    };
    pub use super::magic::MagicTypeError;
}
