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

use std::io::Read;
use std::path::Path;

use itertools::Itertools;
use lopdf;

use super::{
    ComicContainerError, ComicContainerFile, ComicContainerVariant, FilesIter, FindFileResult,
};

pub use self::error::*;

mod error;

/// Create comic book file from PDF page parts. Page position, page name and page content stream
fn pdf_page_data_to_comic_page(
    page_pos: usize,
    page_name: &str,
    page_stream: &lopdf::Stream,
) -> ComicContainerFile {
    ComicContainerFile {
        pos: page_pos, //here we should to save zero based page position
        name: format!("{}_{}", page_pos, page_name), //a lot of PDF pages have equal names. So I add a page number
        content: page_stream.content.to_owned(),
    }
}

/// PDF comic book container which filter only image pages
#[derive(Debug, Clone)]
pub struct PDFComicContainer(lopdf::Document);

impl PDFComicContainer {
    // Read documentation for more information:
    // https://www.adobe.com/content/dam/acom/en/devnet/pdf/pdfs/pdf_reference_archives/PDFReference.pdf

    const KEY_XOBJECT: &'static [u8] = b"XObject";
    const KEY_SUBTYPE: &'static [u8] = b"Subtype";
    const KEY_MASK: &'static [u8] = b"Mask";

    const VALUE_SUBTYPE_IMAGE: &'static [u8] = b"Image";

    /// Create new comic book container from provided PDF [pdf_document]
    fn new(pdf_document: lopdf::Document) -> Self {
        PDFComicContainer(pdf_document)
    }

    /// Open comic book container from provided file descriptor
    pub fn open(src: impl Read) -> Result<Self> {
        lopdf::Document::load_from(src)
            .map(Into::into)
            .map_err(Into::into)
    }

    /// Open comic book container from provided path
    #[allow(dead_code)]
    pub fn open_path(path: impl AsRef<Path>) -> Result<Self> {
        lopdf::Document::load(path)
            .map(Into::into)
            .map_err(Into::into)
    }

    /// Get single comic book page by provided position and PDF page id
    fn get_comic_page_inner(
        &self,
        page_pos: usize,
        page_id: lopdf::ObjectId,
    ) -> Result<Option<ComicContainerFile>> {
        self.get_page_img(page_id).map(|page_data_opt| {
            page_data_opt.map(|(page_name, page_stream)| {
                pdf_page_data_to_comic_page(page_pos, page_name, page_stream)
            })
        })
    }

    /// Get PDF page ID by provided page position [pos]
    fn page_id(&self, pos: usize) -> Option<lopdf::ObjectId> {
        self.0.page_iter().enumerate().find_map(
            |(page_pos, page_id)| {
                if page_pos == pos {
                    Some(page_id)
                } else {
                    None
                }
            },
        )
    }

    /// Extract single image.
    /// Return error if there is more than single image inside the page of if image contains mask
    fn get_page_img(&self, page_id: lopdf::ObjectId) -> Result<Option<(&str, &lopdf::Stream)>> {
        if let (Some(page_resources), _) = self.0.get_page_resources(page_id) {
            //all image streams at the page
            let result = page_resources
                .get(Self::KEY_XOBJECT) //get XObject at the page
                .and_then(|o| o.as_dict())?
                .into_iter() //iterate over all XObject's child objects
                .filter_map(|(bytes, object)| {
                    object
                        .as_reference() //get object reference and find it in the PDF
                        .and_then(|id| self.0.get_object(id))
                        .and_then(|obj| obj.as_stream())
                        .and_then(|stream| {
                            //get stream subtype
                            let subtype = stream
                                .dict
                                .get(Self::KEY_SUBTYPE)
                                .and_then(|o| o.as_name())?;

                            match subtype {
                                //we need only Images
                                Self::VALUE_SUBTYPE_IMAGE => {
                                    //we can get image name. But it can be not unique
                                    let file_name = std::str::from_utf8(bytes)?;

                                    Ok(Some((file_name, stream)))
                                }
                                _ => Ok(None),
                            }
                        })
                        .map_err(|e| {
                            error!(
                                "Can't process PDF image on page id: '{:?}'. Error: '{}'",
                                page_id, e
                            );
                            Error::from(e)
                        })
                        .transpose()
                })
                // take only 2 image to test is it valid comic book
                .take(2)
                .exactly_one();

            // here we get single PDF page daaata (page_name, page_stream)
            match result {
                //filter pages with JBIG2
                Ok(img_data) => img_data.and_then(|img_data| {
                    //check is image has any mask. I decided to not support it now.
                    //For future implementation check jpeg2000 (PDF filer JPXDecode), JBIG2 (PDF filter JBIG2Decode)
                    if img_data.1.dict.has(Self::KEY_MASK) {
                        error!("Current version doesn't support PDF image masks");
                        Err(Error::NotSupported)
                    } else {
                        Ok(Some(img_data))
                    }
                }),
                Err(iter) => {
                    error!("PDF page '{:?}' contains {} images", page_id, iter.count());
                    Err(Error::NotSupported)
                }
            }
        } else {
            Ok(None)
        }
    }
}

impl From<lopdf::Document> for PDFComicContainer {
    fn from(pdf_document: lopdf::Document) -> Self {
        Self::new(pdf_document)
    }
}

impl ComicContainerVariant for PDFComicContainer {
    fn files(&mut self) -> FilesIter {
        Box::new(
            PDFComicIterator {
                pdf: self,
                iter: self.0.page_iter().enumerate(),
            }
            .map(|result| result.map_err(Into::into)),
        )
    }

    fn file_at(&mut self, pos: usize) -> FindFileResult {
        if let Some(page_id) = self.page_id(pos) {
            self.get_comic_page_inner(pos, page_id).map_err(Into::into)
        } else {
            Ok(None)
        }
    }
}

/// Iterator over all comic book pages in the PDF document
#[derive(Debug)]
struct PDFComicIterator<'c, I> {
    pdf: &'c PDFComicContainer,
    iter: I,
}

impl<I> Iterator for PDFComicIterator<'_, I>
where
    I: Iterator<Item = (usize, lopdf::ObjectId)>,
{
    type Item = Result<ComicContainerFile>;

    fn next(&mut self) -> Option<Self::Item> {
        loop {
            let (page_pos, page_id) = self.iter.next()?;

            match self
                .pdf
                .get_comic_page_inner(page_pos, page_id)
                .transpose()?
            {
                Err(Error::NotSupported) => {
                    // silently ignore not supported PDF pages
                    continue;
                }
                result => {
                    return Some(result);
                }
            }
        }
    }
}

#[cfg(test)]
mod test {
    use super::*;

    use crate::comics::container::tests::{base_archive_path, open_archive_fd};

    const PDF: &str = "pdf/comics_test.pdf";

    #[test]
    fn test_pdf() {
        base_test(PDFComicContainer::open_path(base_archive_path().join(PDF)).unwrap());
    }

    fn base_test(mut pdf_container: PDFComicContainer) {
        let files = pdf_container
            .files()
            .filter_map(|file| file.ok())
            .collect::<Vec<_>>();

        assert_eq!(files.len(), 3, "Invalid PDF files count");

        let img_count = files
            .into_iter()
            .map(|f| image::guess_format(f.content.as_slice()))
            .filter_map(image::ImageResult::ok)
            .count();

        assert_eq!(img_count, 3, "Invalid PDF images count")
    }
}
