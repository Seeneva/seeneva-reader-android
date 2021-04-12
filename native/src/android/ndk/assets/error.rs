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

use std::fmt::{Display, Formatter, Result as FmtResult};
use thiserror::Error as DeriveError;

pub type Result<T> = std::result::Result<T, Error>;

#[derive(DeriveError, Debug, Clone)]
#[error("Android assets inner error. Reason: {0}. Comment: {1:?}")]
pub struct Error(SysErrorKind, Option<String>);

impl Error {
    pub(super) fn new_sys_error(kind: SysErrorKind, comment: Option<impl Into<String>>) -> Self {
        Error(kind, comment.map(Into::into))
    }
}

#[derive(Debug, Copy, Clone)]
pub enum SysErrorKind {
    Null,
}

impl Display for SysErrorKind {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        match self {
            Self::Null => writeln!(f, "Null pointer"),
        }
    }
}
