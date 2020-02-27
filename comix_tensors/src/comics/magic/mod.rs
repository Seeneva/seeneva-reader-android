use std::io::{Read, Seek, SeekFrom};

use once_cell::sync::Lazy;

pub use self::error::MagicTypeError;

mod error;

pub type Result<T> = ::std::result::Result<T, MagicTypeError>;

const MAGIC_NUMBERS: [(&[u8], MagicType); 6] = [
    (b"Rar!\x1A\x07\x00", MagicType::RAR),
    (b"Rar!\x1A\x07\x01\x00", MagicType::RAR),
    (b"PK", MagicType::ZIP),
    (b"7z\xBC\xAF'", MagicType::SZ),
    (b"%PDF", MagicType::PDF),
    (b"<?xml version=", MagicType::XML),
];

static MAGIC_MAX_LEN: Lazy<usize> = Lazy::new(|| {
    MAGIC_NUMBERS
        .iter()
        .map(|(binary_repr, _)| binary_repr.len())
        .max()
        .expect("Can't get max length of magic numbers")
});

///All supported file's magic types
#[derive(Debug, Copy, Clone, PartialEq)]
pub enum MagicType {
    RAR,
    ZIP,
    SZ,
    PDF,
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

///Get [input] magic type
pub fn resolve_file_magic_type<T>(input: &mut T) -> Result<MagicType>
where
    T: Read + Seek,
{
    debug!("Try to resolve magic type");

    MagicType::guess_reader_type(input).map(|magic_type| match magic_type {
        Some(magic_type) => Ok(magic_type),
        _ => Err(MagicTypeError::UnknownFormat),
    })?
}
