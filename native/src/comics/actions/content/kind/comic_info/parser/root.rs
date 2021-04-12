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

use crate::comics::actions::content::ComicInfo;

use super::*;

#[derive(Debug, Default)]
pub struct ComicInfoTagParser {
    comic_info: ComicInfo,
    tag_text_buf: Vec<u8>,
}

impl ParserDescription for ComicInfoTagParser {
    type O = ComicInfo;

    fn parse<R>(&mut self, reader: &mut XmlReader<R>, event: Event)
    where
        R: BufRead,
    {
        let comic_info = &mut self.comic_info;

        match event {
            Start(ref e) => match reader.decode(e.local_name()) {
                Ok("Title") => comic_info.title = read_tag_text(reader, e, &mut self.tag_text_buf),
                Ok("Series") => {
                    comic_info.series = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Number") => {
                    comic_info.number = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Count") => comic_info.count = read_tag_text(reader, e, &mut self.tag_text_buf),
                Ok("Web") => comic_info.web = read_tag_text(reader, e, &mut self.tag_text_buf),
                Ok("Volume") => {
                    comic_info.volume = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Summary") => {
                    comic_info.summary = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Notes") => comic_info.notes = read_tag_text(reader, e, &mut self.tag_text_buf),
                Ok("Publisher") => {
                    comic_info.publisher = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Genre") => comic_info.genre = read_tag_text(reader, e, &mut self.tag_text_buf),
                Ok("Format") => {
                    comic_info.format = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("AgeRating") => {
                    comic_info.age_rating = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Teams") => comic_info.teams = read_tag_text(reader, e, &mut self.tag_text_buf),
                Ok("Locations") => {
                    comic_info.locations = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("StoryArc") => {
                    comic_info.story_arc = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("SeriesGroup") => {
                    comic_info.series_group = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("PageCount") => {
                    comic_info.page_count = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("LanguageISO") => {
                    comic_info.language_iso = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Year") => comic_info.year = read_tag_text(reader, e, &mut self.tag_text_buf),
                Ok("Month") => comic_info.month = read_tag_text(reader, e, &mut self.tag_text_buf),
                Ok("Day") => comic_info.day = read_tag_text(reader, e, &mut self.tag_text_buf),
                Ok("Writer") => {
                    comic_info.writer = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Penciller") => {
                    comic_info.penciller = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Inker") => comic_info.inker = read_tag_text(reader, e, &mut self.tag_text_buf),
                Ok("Colorist") => {
                    comic_info.colorist = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Letterer") => {
                    comic_info.letterer = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("CoverArtist") => {
                    comic_info.cover_artist = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Editor") => {
                    comic_info.editor = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Imprint") => {
                    comic_info.imprint = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Characters") => {
                    comic_info.characters = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                Ok("Manga") => {
                    comic_info.manga = read_tag_text::<_, String>(reader, e, &mut self.tag_text_buf)
                        .and_then(|v| yes_no_to_bool(&v))
                }
                Ok("BlackAndWhite") => {
                    comic_info.black_and_white =
                        read_tag_text::<_, String>(reader, e, &mut self.tag_text_buf)
                            .and_then(|v| yes_no_to_bool(&v))
                }
                Ok("Pages") => {
                    comic_info.pages = XmlTagParser::from(PagesTagParser::default()).parse(reader)
                }
                _ => (),
            },
            _ => (),
        };
    }

    fn into_object(self) -> Option<Self::O> {
        Some(self.comic_info)
    }
}
