mod error;
mod rar;
mod seven_zip;
mod tar;
mod zip;

pub use self::error::ComicContainerError;
use super::magic::{resolve_file_magic_type, MagicType, MagicTypeError};

use std::fs::File;
use std::ops::Deref;
use std::os::unix::io::{FromRawFd, RawFd};

use tokio::prelude::*;

/// Comic container file
/// Files usually not sorted by name in a container. So use [pos] to get real file position
/// If [is_dir] than [content] is [Option::None]
#[derive(Debug, Clone)]
pub struct ArchiveFile {
    ///position in the archive.
    pub pos: usize,
    pub name: String,
    pub is_dir: bool,
    pub content: Option<Vec<u8>>,
}

type ComicFilesStream =
    Box<dyn Stream<Item = ArchiveFile, Error = Box<dyn::std::error::Error + Send + Sync>> + Send>;

///Base trait for all supported comics container
pub trait ComicContainer {
    ///Stream all files in the comic container
    fn files(&self) -> ComicFilesStream;
}

///Variants of supported comics containers
#[derive(Debug, Copy, Clone)]
pub enum ComicContainerVariant {
    CBR(rar::RarArchive),
    CBZ(zip::ZipArchive),
    CB7(seven_zip::SevenZipArchive),
    CBT,
}

impl ComicContainerVariant {
    #[cfg(target_family = "unix")]
    pub fn init(fd: RawFd) -> impl Future<Item = Self, Error = ComicContainerError> {
        future::ok(unsafe { File::from_raw_fd(libc::dup(fd)) })
            .and_then(|mut f| resolve_file_magic_type(&mut f))
            .from_err()
            .and_then(move |file_magic| {
                use self::ComicContainerVariant::*;
                use self::MagicType::*;

                match file_magic {
                    RAR => {
                        debug!("It is CBR archive");
                        Ok(CBR(rar::RarArchive::new(fd)))
                    }
                    ZIP => {
                        debug!("It is CBZ archive");
                        Ok(CBZ(zip::ZipArchive::new(fd)))
                    }
                    SZ => {
                        debug!("It is CB7 archive");
                        Ok(CB7(seven_zip::SevenZipArchive::new(fd)))
                    }
                    _ => {
                        error!("Unsupported archive format {:?}", file_magic);
                        Err(ComicContainerError::UnsupportedType(file_magic))
                    }
                }
            })
    }
}

impl Deref for ComicContainerVariant {
    type Target = ComicContainer;

    fn deref(&self) -> &Self::Target {
        use self::ComicContainerVariant::*;

        match self {
            CBR(ref archive) => archive,
            CBZ(ref archive) => archive,
            CB7(ref archive) => archive,
            CBT => unimplemented!(),
        }
    }
}

#[cfg(test)]
pub mod tests {
    use super::*;
    pub use crate::tests::base_test_path;
    use std::env;
    use std::fs::File;
    use std::os::unix::io::IntoRawFd;
    use std::path::PathBuf;

    #[cfg(target_family = "unix")]
    pub fn open_archive_fd(archive_path: &[&str]) -> RawFd {
        let mut path = base_test_path();
        path.push("archive");
        path.extend(archive_path);

        println!("Trying to open file {:?}", path);

        File::open(path).unwrap().into_raw_fd()
    }
}
