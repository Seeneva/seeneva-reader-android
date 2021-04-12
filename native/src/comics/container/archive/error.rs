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

use thiserror::Error as DeriveError;

use libarchive_rs::FileType;

use super::ComicContainerError;

pub type Result<T> = std::result::Result<T, Error>;

#[derive(DeriveError, Debug)]
pub enum Error {
    #[error("Inner libarchive error occurred during archive reading: {0}")]
    Inner(#[from] libarchive_rs::ArchiveError),
    #[error("IO error occurred during archive reading: {0}")]
    IO(#[from] std::io::Error),
    #[error("Archive entry is not a file. It is: '{0:?}'")]
    NotAFile(FileType),
}

impl ComicContainerError for Error {}

impl From<Error> for Box<dyn ComicContainerError> {
    fn from(err: Error) -> Self {
        Box::new(err)
    }
}
