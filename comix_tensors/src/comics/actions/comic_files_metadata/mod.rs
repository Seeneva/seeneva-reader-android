use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};
use std::io::Cursor;

use image::guess_format as guess_image_format;
use tokio::prelude::stream::Fuse;
use tokio::prelude::*;

use crate::comics::archive::ArchiveFile;
use crate::comics::magic::MagicType;
use crate::comics::ComicBookMetadataType;

use self::comic_info::parse_comic_info;

mod comic_info;

const COMIC_INFO_NAME: &str = "ComicInfo.xml";

///Errors occurred while trying to get comic file metadata
#[derive(Debug)]
pub enum GetComicFileMetadataError {
    ///Error occurred than there is no any supported image file
    NoComicPages,
    ///Error occurred than can't open image file in the container
    CantOpenImage(image::ImageError),
}

impl From<image::ImageError> for GetComicFileMetadataError {
    fn from(e: image::ImageError) -> Self {
        GetComicFileMetadataError::CantOpenImage(e)
    }
}

impl Display for GetComicFileMetadataError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        use self::GetComicFileMetadataError::*;

        match self {
            NoComicPages => writeln!(f, "Comic container does not contain any supported images"),
            CantOpenImage(_) => writeln!(
                f,
                "Can't open comic book image file while trying to get it metadata."
            ),
        }
    }
}

impl Error for GetComicFileMetadataError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        use self::GetComicFileMetadataError::*;

        match self {
            CantOpenImage(e) => Some(e),
            _ => None,
        }
    }
}

///Stream which filter comic container files and return all supported variants
pub struct GetComicFileMetadataStream<S> {
    stream: Fuse<S>,
    has_comic_pages: bool,
}

impl<S> GetComicFileMetadataStream<S>
where
    S: Stream,
{
    pub fn new(stream: S) -> GetComicFileMetadataStream<S> {
        GetComicFileMetadataStream {
            stream: stream.fuse(),
            has_comic_pages: false,
        }
    }
}

impl<S, E> Stream for GetComicFileMetadataStream<S>
where
    S: Stream<Item = ArchiveFile, Error = E>,
    E: From<GetComicFileMetadataError>,
{
    type Item = ComicBookMetadataType;
    type Error = S::Error;

    fn poll(&mut self) -> Poll<Option<Self::Item>, Self::Error> {
        loop {
            let res = try_ready!(self.stream.poll());

            match res {
                //Ignore any directory 'files' in the archive
                Some(ref file) if file.is_dir => continue,
                Some(file) => {
                    let content = file
                        .content
                        .as_ref()
                        .expect("Content of the archive file cannot be empty");

                    if is_comic_info_xml(&file.name, content) {
                        if let Some(comic_info) =
                            parse_comic_info(content.as_slice()).map(Into::into)
                        {
                            return Ok(Async::Ready(Some(comic_info)));
                        } else {
                            continue;
                        }
                    } else if let Ok(img_format) = guess_image_format(content) {
                        self.has_comic_pages = true;

                        return image::io::Reader::with_format(Cursor::new(content), img_format)
                            .into_dimensions()
                            .map(|(w, h)| ComicBookMetadataType::ComicPage((file, w, h).into()))
                            .map(|t| Async::Ready(Some(t)))
                            .map_err(|e| GetComicFileMetadataError::from(e).into());
                    } else {
                        info!("Comic container contains unsupported file: '{}'", file.name);
                        continue;
                    }
                }
                None => {
                    return match self.has_comic_pages {
                        true => Ok(Async::Ready(None)),
                        false => Err(GetComicFileMetadataError::NoComicPages.into()),
                    }
                }
            }
        }
    }
}

/// Is provided file ComicInfo.xml with metadata
fn is_comic_info_xml(file_name: &str, file_content: &[u8]) -> bool {
    file_name == COMIC_INFO_NAME && MagicType::is_type(file_content, MagicType::XML)
}
