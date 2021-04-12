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

use super::ComicContainerError;

/// PDF comic book container result
pub type Result<T> = std::result::Result<T, Error>;

/// PDF comic book container error
#[derive(DeriveError, Debug)]
pub enum Error {
    #[error("lopdf inner error: '{0}'")]
    Inner(#[from] lopdf::Error),
    #[error("The PDF file cannot be read")]
    NotSupported,
}

impl ComicContainerError for Error {}

impl From<Error> for Box<dyn ComicContainerError> {
    fn from(err: Error) -> Self {
        Box::new(err)
    }
}
