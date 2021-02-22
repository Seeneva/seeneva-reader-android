use std::io::{Read, Seek, SeekFrom};

use blake2::{Blake2b, Digest};

///Calculate hash value of the provided [source]. Return tuple (file_size, file_hash)
pub fn blake_hash(source: &mut (impl Read + Seek)) -> std::io::Result<(u64, [u8; 64])> {
    let mut hasher = Blake2b::new();

    let size = source
        .seek(SeekFrom::Start(0))
        .and(std::io::copy(source, &mut hasher))?;

    let mut hash = [0u8; 64];

    hash.copy_from_slice(&hasher.finalize());

    Ok((size, hash))
}
