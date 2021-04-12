/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
