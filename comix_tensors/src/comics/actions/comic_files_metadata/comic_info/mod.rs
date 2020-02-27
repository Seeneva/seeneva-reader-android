use std::io::BufRead;

use crate::comics::ComicInfo;

use self::parser::*;

mod parser;

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
