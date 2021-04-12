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

use super::Status;

pub type Result<T> = std::result::Result<T, Error>;

#[derive(DeriveError, Debug)]
pub enum Error {
    #[error("Android bitmap JNI error. Reason: '{0}'")]
    Jni(#[from] jni::errors::Error),
    #[error("Android bitmap inner error. Reason: '{0:?}'")]
    Sys(Status),
}

impl From<Status> for Error {
    fn from(status: Status) -> Self {
        Error::Sys(status)
    }
}
