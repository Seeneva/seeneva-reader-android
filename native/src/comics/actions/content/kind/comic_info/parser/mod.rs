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
use std::str::FromStr;

use quick_xml::events::attributes::Attribute;
use quick_xml::events::BytesStart;
use quick_xml::events::Event;
use quick_xml::Reader as XmlReader;

use super::ComicInfoPage;

pub(super) use self::pages::*;
pub(super) use self::root::*;

use self::Event::*;

mod page;
mod pages;
mod root;

///Parse XML using provided parser
pub fn parse_xml<R, P>(reader: R, parser: P) -> Option<P::O>
where
    R: BufRead,
    P: ParserDescription,
{
    let mut reader = XmlReader::from_reader(reader);

    XmlTagParser::from(parser).parse(&mut reader)
}

struct XmlTagParser<P> {
    depth: u32, //How deep in the XML object
    inner_parser: P,
}

impl<P> XmlTagParser<P>
where
    P: ParserDescription,
{
    fn new(inner_parser: P) -> XmlTagParser<P> {
        XmlTagParser {
            depth: 1,
            inner_parser,
        }
    }

    ///Read XML while it has more items or while [inner_parser] doesn't finish parsing
    fn parse<R>(mut self, reader: &mut XmlReader<R>) -> Option<P::O>
    where
        R: BufRead,
    {
        let mut buf = vec![];

        //do while reader doesn't quit XML object of the inner_parser
        while self.depth > 0 {
            match reader.read_event(&mut buf) {
                Err(e) => {
                    error!("Can't parse ComicInfo.xml. Error: '{}'", e.to_string());
                    return None;
                }
                Ok(event) => {
                    match event {
                        Eof => break,
                        Start(_) => self.depth += 1,
                        End(_) => self.depth -= 1,
                        _ => (),
                    }

                    self.inner_parser.parse(reader, event);
                }
            }

            buf.clear();
        }

        self.inner_parser.into_object()
    }
}

///Describe how to parse events to object [O]
pub trait ParserDescription {
    type O;

    /// Parse XML events
    fn parse<R>(&mut self, reader: &mut XmlReader<R>, event: Event)
    where
        R: BufRead;

    ///Consume and return parsed object if any
    fn into_object(self) -> Option<Self::O>;
}

impl<P> From<P> for XmlTagParser<P>
where
    P: ParserDescription,
{
    fn from(parser: P) -> Self {
        XmlTagParser::new(parser)
    }
}

///Read text between <tag>...</tag> and cast result to specific type T
fn read_tag_text<R, T>(
    reader: &mut XmlReader<R>,
    start: &BytesStart,
    buf: &mut Vec<u8>,
) -> Option<T>
where
    R: BufRead,
    T: FromStr,
{
    buf.clear();

    reader
        .read_text(start.name(), buf)
        .ok()
        .and_then(|n| str::parse::<T>(n.as_str()).ok())
}

///Read tag's attribute text and cast result to specific type T
fn read_tag_attribute<R, T>(reader: &XmlReader<R>, attr: Attribute) -> Option<T>
where
    R: BufRead,
    T: FromStr,
{
    attr.unescape_and_decode_value(reader)
        .ok()
        .and_then(|decoded_val| str::parse::<T>(&decoded_val).ok())
}

/// Convert Yes and No to bool
fn yes_no_to_bool<S: AsRef<str>>(s: &S) -> Option<bool> {
    match s.as_ref() {
        "Yes" => Some(true),
        "No" => Some(false),
        _ => None,
    }
}
