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

use crate::comics::container::ComicContainerError;
use crate::task::CancelledError;

#[derive(DeriveError, Debug)]
pub enum GetComicRawImageError {
    ///Error occurred while trying to find raw comic book page
    #[error("Can't get comic book raw page. Container error: '{0}'")]
    ContainerError(#[from] Box<dyn ComicContainerError>),
    ///Error occurred when tried to open image
    #[error("Can't open comic book page as image. Error: '{0}'")]
    ImgError(#[from] image::ImageError),
    /// Task was cancelled
    #[error("{0}")]
    Cancelled(#[from] CancelledError),
}

#[derive(DeriveError, Debug, Copy, Clone)]
#[error("{0}")]
pub struct ResizeComicImageError(#[from] pub CancelledError);
