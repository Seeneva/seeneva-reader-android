mod parser;

use self::parser::*;
use crate::ComicInfo;

///Parse XML as ComicRack format.
///
///<?xml version="1.0" encoding="utf-8"?>
///<ComicInfo xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
///   <Series>Dragon Ball</Series>
///   <Volume>1</Volume>
///   <Writer>Akira Toriyama</Writer>
///   <ScanInformation>MCD(3106)</ScanInformation>
///   <Pages>
///      <Page Image="0" Bookmark="Cover"/>
///      <Page Image="4" Bookmark="Table of Contents"/>
///      <Page Image="6" Bookmark="Tale 1: Bloomers and the Monkey king"/>
///      <Page Image="36" Bookmark="Tale 2: No Balls!"/>
///      <Page Image="50" Bookmark="Tale 3: Sea Monkeys"/>
///      <Page Image="64" Bookmark="Tale 4: They Call Him... the Turtle Hermit!"/>
///      <Page Image="78" Bookmark="Tale 5: Oo! Oo! Oolong!"/>
///      <Page Image="92" Bookmark="Tale 6: So Long, Oolong!"/>
///      <Page Image="106" Bookmark="Tale 7: Yamcha and Pu'ar"/>
///      <Page Image="120" Bookmark="Tale 8: One, Two, Yamcha-cha!"/>
///      <Page Image="134" Bookmark="Tale 9: Dragon Balls in Danger"/>
///      <Page Image="148" Bookmark="Tale 10: Onward to Fry-Pan"/>
///      <Page Image="162" Bookmark="Tale 11: ...And into the Fire!"/>
///      <Page Image="176" Bookmark="Title Page Gallery"/>
///   </Pages>
///</ComicInfo>
pub fn parse_comic_info(content: &[u8]) -> Option<ComicInfo> {
    parse_xml(content, ComicInfoTagParser::default())
}
