mod comic_info;

use self::comic_info::parse_comic_info;
use crate::comics::archive::ArchiveFile;
use crate::comics::magic::MagicType;
use crate::comics::ComicFile;

use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};

use image::guess_format as guess_image_format;
use tokio::prelude::*;

const COMIC_INFO_NAME: &str = "ComicInfo.xml";

///Error occurred than there is no any supported image file
#[derive(Debug, Copy, Clone)]
pub struct NoComicPages;

impl Display for NoComicPages {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        writeln!(f, "Comic container does not contain any supported images")
    }
}

impl Error for NoComicPages {}

///Stream which filter comic container files and return all supported variants
pub struct FilterComicFilesStream<S> {
    stream: S,
    has_comic_pages: bool,
}

impl<S> FilterComicFilesStream<S> {
    pub fn new(stream: S) -> Self {
        FilterComicFilesStream {
            stream,
            has_comic_pages: false,
        }
    }
}

impl<S, E> Stream for FilterComicFilesStream<S>
where
    S: Stream<Item = ArchiveFile, Error = E>,
    E: From<NoComicPages>,
{
    type Item = ComicFile;
    type Error = S::Error;

    fn poll(&mut self) -> Poll<Option<Self::Item>, Self::Error> {
        loop {
            let res = match self.stream.poll() {
                Err(e) => return Err(e.into()),
                Ok(Async::NotReady) => return Ok(Async::NotReady),
                Ok(Async::Ready(res)) => res,
            };

            match res {
                //Ignore any directory 'files' in the archive
                Some(ref file) if file.is_dir => continue,
                Some(file) => {
                    let content = file.content.as_ref().unwrap();

                    if is_comic_info_xml(&file.name, content) {
                        //Try to parse comic info from XML
                        //Just ignore it if it can't be read
                        if let Some(comic_info) = parse_comic_info(content) {
                            return Ok(Async::Ready(Some(comic_info.into())));
                        }
                    } else if is_image(content) {
                        self.has_comic_pages = true;
                        return Ok(Async::Ready(Some(ComicFile::ComicPage(file.into()))));
                    }

                    info!("Comic container contains unsupported file: '{}'", file.name);

                    continue;
                }
                None => {
                    return match self.has_comic_pages {
                        true => Ok(Async::Ready(None)),
                        false => Err(NoComicPages.into()),
                    };
                }
            }
        }
    }
}

///Check is provided file is image
fn is_image(file_content: &[u8]) -> bool {
    guess_image_format(file_content).is_ok()
}

/// Is provided file ComicInfo.xml with metadata
fn is_comic_info_xml(file_name: &str, file_content: &[u8]) -> bool {
    file_name == COMIC_INFO_NAME && MagicType::is_type(file_content, MagicType::XML)
}
