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

use std::io::{Read, Result as IoResult, Seek, SeekFrom};

use once_cell::sync::Lazy;

static MAGIC_MAX_LEN: Lazy<usize> = Lazy::new(|| {
    MagicType::NUMBERS
        .iter()
        .map(|(binary_repr, _)| binary_repr.len())
        .max()
        .expect("Can't get max length of magic numbers")
});

/// File's magic types
#[derive(Debug, Copy, Clone, PartialEq)]
pub enum MagicType {
    PDF,
    XML,
}

impl MagicType {
    const NUMBERS: [(&'static [u8], MagicType); 2] = [
        (b"%PDF", MagicType::PDF),
        (b"<?xml version=", MagicType::XML),
        // (b"Rar!\x1A\x07\x00", MagicType::RAR),
        // (b"Rar!\x1A\x07\x01\x00", MagicType::RAR),
        // (b"PK", MagicType::ZIP),
        // (b"7z\xBC\xAF'", MagicType::SZ),
    ];

    ///Guess the [MagicType] of the provided [input]
    pub fn guess_reader_type<T>(input: &mut T) -> IoResult<Option<Self>>
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
        for (binary_repr, magic_type) in Self::NUMBERS.iter() {
            if input.starts_with(binary_repr) {
                return Some(*magic_type);
            }
        }

        None
    }

    ///Check is provided [input] is for provided [magic_type]
    pub fn is_type(input: &[u8], magic_type: MagicType) -> bool {
        Self::guess_type(input)
            .map(|t| t == magic_type)
            .unwrap_or(false)
    }
}
