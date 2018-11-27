mod archive;
mod magic;
mod preprocessor;

use crate::InterpreterInput;

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

/// All possible variants of comic files
#[derive(Debug, Clone)]
pub enum ComicFile {
    ///Image file as the TF model input
    Image(InterpreterInput),
    ///Comic parsed metadata
    ComicInfo(ComicInfo),
}

pub mod prelude {
    pub use super::archive::{ComicContainerError, ComicContainerVariant};
    pub use super::magic::MagicTypeError;
    pub use super::preprocessor::{ComicPreprocessError, ComicPreprocessing};
    pub use super::{ComicFile, ComicInfo, ComicInfoPage};
}
