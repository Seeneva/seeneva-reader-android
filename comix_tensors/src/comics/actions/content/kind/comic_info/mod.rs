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
