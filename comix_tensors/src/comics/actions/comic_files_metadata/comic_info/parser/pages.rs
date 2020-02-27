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
