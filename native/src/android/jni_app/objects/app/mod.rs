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

use super::{constants, field_pointer, java, string_to_jobject, JniClassConstructorCache};

pub mod comic_book_page;
pub mod comic_book_page_object;
pub mod comic_book_result;
pub mod comic_page_image_data;
pub mod comic_rack;
pub mod error;
pub mod file_hash;
pub mod ml;
pub mod task;
