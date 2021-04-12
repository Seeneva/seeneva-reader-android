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

use std::io::BufRead;

use self::parser::{parse_xml, ComicInfoTagParser};

mod parser;

#[derive(Debug, Clone, Default)]
pub struct ComicInfo {
    pub title: Option<String>,
    pub series: Option<String>,
    pub summary: Option<String>,
    pub number: Option<u32>,
    pub count: Option<u32>,
    pub volume: Option<u32>,
    pub page_count: Option<u32>,
    pub year: Option<u16>,
    pub month: Option<u8>,
    pub day: Option<u8>,
    pub publisher: Option<String>,
    pub writer: Option<String>,
    pub penciller: Option<String>,
    pub inker: Option<String>,
    pub colorist: Option<String>,
    pub letterer: Option<String>,
    pub cover_artist: Option<String>,
    pub editor: Option<String>,
    pub imprint: Option<String>,
    pub genre: Option<String>,
    pub format: Option<String>,
    pub age_rating: Option<String>,
    pub teams: Option<String>,
    pub locations: Option<String>,
    pub story_arc: Option<String>,
    pub series_group: Option<String>,
    pub black_and_white: Option<bool>,
    pub manga: Option<bool>,
    pub characters: Option<String>,
    pub web: Option<String>,
    pub notes: Option<String>,
    pub language_iso: Option<String>,
    pub pages: Option<Vec<ComicInfoPage>>,
}

/// Metadata of a single comic page
#[derive(Debug, Clone, Default)]
pub struct ComicInfoPage {
    /// id
    pub image: Option<u32>,
    pub image_type: Option<String>,
    pub image_size: Option<usize>,
    pub image_width: Option<u32>,
    pub image_height: Option<u32>,
}

impl ComicInfoPage {
    /// Is this comic book page is a cover
    pub fn is_cover(&self) -> bool {
        match &self.image_type {
            Some(t) => t == "ComicInfoPage",
            _ => false,
        }
    }
}

///Parse XML as ComicRack format.
///
///<?xml version="1.0"?>
///<ComicInfo xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
///<Title>Hope And Glory - Part II: Bitter Beginnings</Title>
///<Series>Ninjak</Series>
///<Number>3</Number>
///<Count>6</Count>
///<Volume>1994</Volume>
///<StoryArc>Arthur</StoryArc>
///<SeriesGroup>Islands</SeriesGroup>
///<Summary>The secret origin of Ninjak continues!</Summary>
///<Notes>Scraped metadata from ComicVine [CVDB141693].</Notes>
///<Year>1995</Year>
///<Month>6</Month>
///<Day>24</Day>
///<Writer>Mark Moretti</Writer>
///<Penciller>Bob McLeod, Mark Moretti</Penciller>
///<Inker>Bob McLeod, Dick Giordano</Inker>
///<Colorist>Kathryn Bolinger</Colorist>
///<Letterer>Bob McLeod, Dick Giordano</Letterer>
///<CoverArtist>Bob McLeod, Kathryn Bolinger, Mark Moretti</CoverArtist>
///<Editor>Bob Layton</Editor>
///<Publisher>Valiant</Publisher>
///<Imprint>Aircel Publishing</Imprint>
///<Genre>Action, Fantasy</Genre>
///<Web>http://www.comicvine.com/ninjak-00-hope-and-glory-part-ii-bitter-beginnings/4000-141693/</Web>
///<PageCount>35</PageCount>
///<LanguageISO>en</LanguageISO>
///<Format>Director's Cut</Format>
///<AgeRating>Mature 17+</AgeRating>
///<BlackAndWhite>No</BlackAndWhite>
///<Manga>No</Manga>
///<Characters>Crimson Dragon, Dr. Silk, Fitzhugh, Iwatsu, Michiko Okubo, Neville Alcott, Ninjak, Senator Yusaku Okubo</Characters>
///<Teams>X-Men</Teams>
///<Locations>California, England, Japan, London, Tokyo</Locations>
///<Pages>
///<Page Image="0" ImageSize="568730" ImageWidth="1280" ImageHeight="1977" Type="FrontCover" />
///<Page Image="1" ImageSize="709786" ImageWidth="1280" ImageHeight="1995" />
///</Pages>
///</ComicInfo>
pub fn parse_comic_info(reader: impl BufRead) -> Option<ComicInfo> {
    parse_xml(reader, ComicInfoTagParser::default())
}

#[cfg(test)]
mod test {
    use std::fs::read;

    use crate::tests::base_test_path;

    use super::*;

    #[test]
    fn test_parse_comic_info() {
        let xml_content = read(base_test_path().join("ComicInfo.xml")).unwrap();

        let comic_info = parse_comic_info(xml_content.as_slice()).unwrap();

        assert_eq!(comic_info.title, Some("Title".to_owned()));
        assert_eq!(comic_info.series, Some("Series".to_owned()));
        assert_eq!(comic_info.summary, Some("Summary".to_owned()));
        assert_eq!(comic_info.number, Some(1));
        assert_eq!(comic_info.count, Some(100));
        assert_eq!(comic_info.volume, Some(1));
        assert_eq!(comic_info.page_count, Some(3));
        assert_eq!(comic_info.year, Some(1999));
        assert_eq!(comic_info.month, Some(4));
        assert_eq!(comic_info.day, Some(26));
        assert_eq!(comic_info.publisher, Some("Publisher".to_owned()));
        assert_eq!(comic_info.writer, Some("Writer".to_owned()));
        assert_eq!(comic_info.penciller, Some("Penciller".to_owned()));
        assert_eq!(comic_info.inker, Some("Inker".to_owned()));
        assert_eq!(comic_info.colorist, Some("Colorist".to_owned()));
        assert_eq!(comic_info.letterer, Some("Letterer".to_owned()));
        assert_eq!(comic_info.cover_artist, Some("CoverArtist".to_owned()));
        assert_eq!(comic_info.editor, Some("Editor".to_owned()));
        assert_eq!(comic_info.imprint, Some("Imprint".to_owned()));
        assert_eq!(comic_info.genre, Some("Genre".to_owned()));
        assert_eq!(comic_info.format, Some("Format".to_owned()));
        assert_eq!(comic_info.age_rating, Some("AgeRating".to_owned()));
        assert_eq!(comic_info.teams, Some("Teams".to_owned()));
        assert_eq!(comic_info.locations, Some("Locations".to_owned()));
        assert_eq!(comic_info.story_arc, Some("StoryArc".to_owned()));
        assert_eq!(comic_info.series_group, Some("SeriesGroup".to_owned()));
        assert_eq!(comic_info.black_and_white, Some(true));
        assert_eq!(comic_info.manga, Some(true));
        assert_eq!(comic_info.characters, Some("Characters".to_owned()));
        assert_eq!(comic_info.web, Some("Web".to_owned()));
        assert_eq!(comic_info.notes, Some("Notes".to_owned()));
        assert_eq!(comic_info.language_iso, Some("LanguageISO".to_owned()));

        let pages = comic_info.pages.unwrap();

        assert_eq!(pages.len(), 3, "Invalid comic info pages count");

        for (i, page) in pages.into_iter().enumerate() {
            assert_eq!(page.image, Some(i as u32));
            assert_eq!(
                page.image_type,
                if i == 0 {
                    Some("FrontCover".to_owned())
                } else {
                    None
                }
            );
            assert_eq!(page.image_width, Some(1988));
            assert_eq!(page.image_height, Some(3056));
        }
    }
}
