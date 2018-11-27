mod process_comic_files;

pub use self::process_comic_files::{
    process as process_comic_files, ProcessError as PreprocessComicFilesError,
};
