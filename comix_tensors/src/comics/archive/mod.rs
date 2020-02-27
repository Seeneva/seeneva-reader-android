use std::io::{Seek, SeekFrom};
use std::ops::Deref;

use blake2::{Blake2b, Digest};
use tokio::prelude::*;

use crate::FileRawFd;

use super::magic::{resolve_file_magic_type, MagicType, MagicTypeError};

pub use self::error::{CalcArchiveHashError, ComicContainerError, InitComicContainerError};

mod error;
mod rar;
mod seven_zip;
//mod tar;
mod pdf;
mod zip;

//TODO ArchiveFile can be present as enum

/// Comic container file
/// Files usually not sorted by name in a container. So use [pos] to get real file position
/// If [is_dir] than [content] is [Option::None]
#[derive(Debug, Clone)]
pub struct ArchiveFile {
    ///position in the archive. 0 based
    pub pos: usize,
    pub name: String,
    pub is_dir: bool,
    pub content: Option<Vec<u8>>,
}

type ComicFilesStream =
    Box<dyn Stream<Item = ArchiveFile, Error = Box<dyn ComicContainerError>> + Send>;

type FindFileFuture =
    Box<dyn Future<Item = Option<ArchiveFile>, Error = Box<dyn ComicContainerError>> + Send>;

///Base trait for all supported comics container
pub trait ComicContainer {
    ///Stream all files in the comic container
    fn files(&self) -> ComicFilesStream;

    ///Find file by it position [pos]. Position is 0 based.
    fn file_by_position(&self, pos: usize) -> FindFileFuture;
}

///Calculate hash value of the provided [source]. Return tuple (file_size, file_hash)
pub fn hash_metadata(
    source: &mut (impl Read + Seek),
) -> Result<(u64, impl AsRef<[u8]>), CalcArchiveHashError> {
    let mut hasher = Blake2b::new();

    let size = source
        .seek(SeekFrom::Start(0))
        .and(std::io::copy(source, &mut hasher))?;

    let hash = hasher.result();

    Ok((size, hash))
}

///Variants of supported comics containers
#[derive(Debug)]
pub enum ComicContainerVariant {
    CBR(rar::RarArchive),
    CBZ(zip::ZipArchive),
    CB7(seven_zip::SevenZipArchive),
    PDF(pdf::PdfArchive),
    //CBT,
}

impl ComicContainerVariant {
    pub fn init<T>(input: T) -> Result<Self, InitComicContainerError>
    where
        T: Into<FileRawFd>,
    {
        let mut input = input.into();

        resolve_file_magic_type(&mut input)
            .map_err(Into::into)
            .and_then(|file_magic| {
                use self::ComicContainerVariant::*;

                match file_magic {
                    MagicType::RAR => {
                        debug!("It is CBR archive");
                        Ok(CBR(rar::RarArchive::new(input)))
                    }
                    MagicType::ZIP => {
                        debug!("It is CBZ archive");
                        Ok(CBZ(zip::ZipArchive::new(input)))
                    }
                    MagicType::SZ => {
                        debug!("It is CB7 archive");
                        Ok(CB7(seven_zip::SevenZipArchive::new(input)))
                    }
                    MagicType::PDF => {
                        debug!("It is PDF");
                        Ok(PDF(pdf::PdfArchive::new(input)))
                    }
                    _ => {
                        error!("Unsupported archive format {:?}", file_magic);
                        Err(InitComicContainerError::UnsupportedType(file_magic))
                    }
                }
            })
    }
}

impl Deref for ComicContainerVariant {
    type Target = dyn ComicContainer;

    fn deref(&self) -> &Self::Target {
        use self::ComicContainerVariant::*;

        match self {
            CBR(archive) => archive,
            CBZ(archive) => archive,
            CB7(archive) => archive,
            PDF(archive) => archive, //CBT => unimplemented!(),
        }
    }
}

#[cfg(test)]
pub mod tests {
    use std::env;
    use std::fs::File;
    use std::os::unix::io::IntoRawFd;
    use std::path::PathBuf;

    use super::*;

    //pub use crate::tests::base_test_path;

    //    #[cfg(target_family = "unix")]
    //    pub fn open_archive_fd(archive_path: &[&str]) -> RawFd {
    //        let mut path = base_test_path();
    //        path.push("archive");
    //        path.extend(archive_path);
    //
    //        println!("Trying to open file {:?}", path);
    //
    //        File::open(path).unwrap().into_raw_fd()
    //    }
}
