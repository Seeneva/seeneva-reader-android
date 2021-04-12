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

use super::ComicInfoPage;

use super::page::*;
use super::*;

#[derive(Debug, Default)]
pub struct PagesTagParser {
    pages: Vec<ComicInfoPage>,
}

impl ParserDescription for PagesTagParser {
    type O = Vec<ComicInfoPage>;

    fn parse<R>(&mut self, reader: &mut XmlReader<R>, event: Event)
    where
        R: BufRead,
    {
        if let Empty(ref e) = event {
            if let Some(page) = e.parse_page(reader) {
                self.pages.push(page);
            }
        }
    }

    fn into_object(self) -> Option<Self::O> {
        if self.pages.len() == 0 {
            None
        } else {
            Some(self.pages)
        }
    }
}
