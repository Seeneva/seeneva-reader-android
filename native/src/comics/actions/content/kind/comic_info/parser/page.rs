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

use super::*;

const IMAGE_ATTRIBUTE_NAME: &str = "Image";
const TYPE_ATTRIBUTE_NAME: &str = "Type";
const IMAGE_SIZE_ATTRIBUTE_NAME: &str = "ImageSize";
const IMAGE_HEIGHT_ATTRIBUTE_NAME: &str = "ImageHeight";
const IMAGE_WIDTH_ATTRIBUTE_NAME: &str = "ImageWidth";

pub trait PageParser {
    fn parse_page<R>(&self, reader: &mut XmlReader<R>) -> Option<ComicInfoPage>
    where
        R: BufRead;
}

impl PageParser for BytesStart<'_> {
    fn parse_page<R>(&self, reader: &mut XmlReader<R>) -> Option<ComicInfoPage>
    where
        R: BufRead,
    {
        let mut page: Option<ComicInfoPage> = None;

        self.attributes().filter_map(Result::ok).for_each(|attr| {
            match reader.decode(attr.key) {
                Ok(IMAGE_ATTRIBUTE_NAME) => {
                    //TODO custom macro?
                    page.get_or_insert(ComicInfoPage::default()).image =
                        read_tag_attribute(reader, attr)
                }
                Ok(TYPE_ATTRIBUTE_NAME) => {
                    page.get_or_insert(ComicInfoPage::default()).image_type =
                        read_tag_attribute(reader, attr)
                }
                Ok(IMAGE_SIZE_ATTRIBUTE_NAME) => {
                    page.get_or_insert(ComicInfoPage::default()).image_size =
                        read_tag_attribute(reader, attr)
                }
                Ok(IMAGE_HEIGHT_ATTRIBUTE_NAME) => {
                    page.get_or_insert(ComicInfoPage::default()).image_height =
                        read_tag_attribute(reader, attr)
                }
                Ok(IMAGE_WIDTH_ATTRIBUTE_NAME) => {
                    page.get_or_insert(ComicInfoPage::default()).image_width =
                        read_tag_attribute(reader, attr)
                }
                _ => (),
            }
        });

        page
    }
}
