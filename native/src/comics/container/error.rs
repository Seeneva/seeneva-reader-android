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

///Errors returned while trying to init ComicContainer
#[derive(DeriveError, Debug)]
pub enum InitComicContainerError {
    /// Returned in case of unsupported file
    #[error("Cannot be opened as comic book container")]
    Unsupported,
    #[error("Can't open file as comic book container. IO error: '{0}'")]
    IO(#[from] std::io::Error),
    #[error("Can't open file as comic book container. Container error: '{0}'")]
    ContainerError(#[from] Box<dyn ComicContainerError>),
}

impl<E: ComicContainerError + 'static> From<E> for InitComicContainerError {
    fn from(container_error: E) -> Self {
        Self::ContainerError(Box::new(container_error))
    }
}

///Base trait for comic book containers errors
pub trait ComicContainerError:
    std::error::Error + thiserror::private::AsDynError + Send + Sync
{
}
