use super::*;

//const ROOT_TAG_NAME: &str = "ComicInfo";
const TITLE_TAG_NAME: &str = "Title";
const SERIES_TAG_NAME: &str = "Series";
const NUMBER_TAG_NAME: &str = "Number";
const COUNT_TAG_NAME: &str = "Count";
const WEB_TAG_NAME: &str = "Web";
const VOLUME_TAG_NAME: &str = "Volume";
const SUMMARY_TAG_NAME: &str = "Summary";
const NOTES_TAG_NAME: &str = "Notes";
const PUBLISHER_TAG_NAME: &str = "Publisher";
const GENRE_TAG_NAME: &str = "Genre";
const PAGE_COUNT_TAG_NAME: &str = "PageCount";
const LANGUAGE_ISO_TAG_NAME: &str = "LanguageISO";
const YEAR_TAG_NAME: &str = "Year";
const MONTH_TAG_NAME: &str = "Month";
const WRITER_TAG_NAME: &str = "Writer";
const PENCILLER_TAG_NAME: &str = "Penciller";
const COVER_ARTIST_TAG_NAME: &str = "CoverArtist";
const CHARACTERS_TAG_NAME: &str = "Characters";
const MANGA_TAG_NAME: &str = "Manga";
const BLACK_AND_WHITE_TAG_NAME: &str = "BlackAndWhite";
const PAGES_TAG_NAME: &str = "Pages";

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
            Start(ref e) => match reader.decode(e.local_name()).as_ref() {
                TITLE_TAG_NAME => {
                    comic_info.title = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                SERIES_TAG_NAME => {
                    comic_info.series = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                NUMBER_TAG_NAME => {
                    comic_info.number = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                COUNT_TAG_NAME => {
                    comic_info.count = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                WEB_TAG_NAME => comic_info.web = read_tag_text(reader, e, &mut self.tag_text_buf),
                VOLUME_TAG_NAME => {
                    comic_info.volume = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                SUMMARY_TAG_NAME => {
                    comic_info.summary = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                NOTES_TAG_NAME => {
                    comic_info.notes = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                PUBLISHER_TAG_NAME => {
                    comic_info.publisher = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                GENRE_TAG_NAME => {
                    comic_info.genre = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                PAGE_COUNT_TAG_NAME => {
                    comic_info.page_count = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                LANGUAGE_ISO_TAG_NAME => {
                    comic_info.language_iso = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                YEAR_TAG_NAME => comic_info.year = read_tag_text(reader, e, &mut self.tag_text_buf),
                MONTH_TAG_NAME => {
                    comic_info.month = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                WRITER_TAG_NAME => {
                    comic_info.writer = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                PENCILLER_TAG_NAME => {
                    comic_info.penciller = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                COVER_ARTIST_TAG_NAME => {
                    comic_info.cover_artist = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                CHARACTERS_TAG_NAME => {
                    comic_info.characters = read_tag_text(reader, e, &mut self.tag_text_buf)
                }
                MANGA_TAG_NAME => {
                    comic_info.manga = read_tag_text::<_, String>(reader, e, &mut self.tag_text_buf)
                        .map(yes_no_to_bool)
                        .unwrap_or(false)
                }
                BLACK_AND_WHITE_TAG_NAME => {
                    comic_info.black_and_white =
                        read_tag_text::<_, String>(reader, e, &mut self.tag_text_buf)
                            .map(yes_no_to_bool)
                            .unwrap_or(false)
                }
                PAGES_TAG_NAME => {
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
