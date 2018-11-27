mod error;

pub use self::error::MagicTypeError;

use std::fs::File;
use std::io::{Read, Seek, SeekFrom};

use lazy_static::lazy_static;

use tokio::prelude::*;

pub type Result<T> = ::std::result::Result<T, MagicTypeError>;

lazy_static! {
    ///Magic numbers for each supported magic types
    static ref MAGIC_NUMBERS: [(&'static [u8], MagicType); 5] = [
        (b"Rar!\x1A\x07\x00", MagicType::RAR),
        (b"Rar!\x1A\x07\x01\x00", MagicType::RAR),
        (b"PK", MagicType::ZIP),
        (b"7z\xBC\xAF'", MagicType::SZ),
        (b"<?xml version=", MagicType::XML),
    ];
    static ref MAGIC_MAX_LEN: usize = MAGIC_NUMBERS
        .iter()
        .map(|(binary_repr, _)| binary_repr.len())
        .max()
        .expect("Can't get max length of magic numbers");
}

///All supported file's magic types
#[derive(Debug, Copy, Clone, PartialEq)]
pub enum MagicType {
    RAR,
    ZIP,
    SZ,
    XML,
}

impl MagicType {
    ///Guess the [MagicType] of the provided [input]
    fn guess_reader_type<T>(input: &mut T) -> Result<Option<Self>>
    where
        T: Read + Seek,
    {
        let mut buf = vec![0u8; *MAGIC_MAX_LEN];

        let seek = SeekFrom::Start(0);

        input.seek(seek)?;
        input.read_exact(&mut buf)?;
        input.seek(seek)?;

        Ok(Self::guess_type(&buf))
    }

    fn guess_type(input: &[u8]) -> Option<Self> {
        for (binary_repr, magic_type) in MAGIC_NUMBERS.iter() {
            if input.starts_with(binary_repr) {
                return Some(*magic_type);
            }
        }

        error!("Unknown file magic numbers: {:?}", input);

        None
    }

    ///Check is provided [input] is for provided [magic_type]
    pub fn is_type(input: &[u8], magic_type: MagicType) -> bool {
        Self::guess_type(input)
            .map(|t| t == magic_type)
            .unwrap_or(false)
    }
}

///Get [file] m,afic type
pub fn resolve_file_magic_type(file: &mut File) -> Result<MagicType> {
    debug!("Try to resolve file magic type");

    let is_file = file.metadata()?.is_file();

    if !is_file {
        return Err(MagicTypeError::NotFile);
    }

    MagicType::guess_reader_type(file).map(|magic_type| match magic_type {
        Some(magic_type) => Ok(magic_type),
        _ => Err(MagicTypeError::UnknownFormat),
    })?
}
