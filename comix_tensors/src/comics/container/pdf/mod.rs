use std::ops::Deref;

use lopdf::{Document as PdfDocument, ObjectId as PdfObjectId};
use tokio::prelude::*;

use crate::FileRawFd;

use super::{ArchiveFile, ComicContainer, ComicContainerError, ComicFilesStream, FindFileFuture};

use self::error::Error;
use std::collections::BTreeMap;

mod error;

type PdfResult<T> = std::result::Result<T, Error>;

#[derive(Debug)]
pub struct PdfArchive {
    fd: FileRawFd,
}

impl PdfArchive {
    ///Create from a file descriptor helper
    pub fn new(fd: FileRawFd) -> Self {
        PdfArchive { fd }
    }

    ///Open PDF
    fn open(&self) -> impl Future<Item = OpenedPdfArchive, Error = Error> {
        future::result(self.fd.dup())
            .from_err()
            .and_then(|fd| PdfDocument::load_from(fd).map_err(Into::into))
            .map(Into::into)
    }

    ///Stream all images per page in the PDF
    fn stream_files(&self) -> impl Stream<Item = ArchiveFile, Error = Error> {
        self.open().map(stream::iter_result).flatten_stream()
    }

    ///Create [Future] which tries to find PDF image at page by [pos]
    fn file_by_page_future(
        &self,
        pos: u32,
    ) -> impl Future<Item = Option<ArchiveFile>, Error = Error> {
        self.open()
            .and_then(move |archive| future::result(archive.file_by_page(pos)))
    }
}

impl ComicContainer for PdfArchive {
    fn files(&self) -> ComicFilesStream {
        Box::new(self.stream_files().from_err())
    }

    fn file_by_position(&self, pos: usize) -> FindFileFuture {
        Box::new(self.file_by_page_future(pos as _).from_err())
    }
}

///Wrapper around PDF document
#[derive(Debug, Clone)]
struct OpenedPdfArchive {
    pdf_doc: PdfDocument,
    pages: BTreeMap<u32, PdfObjectId>, //page number -> PDF page object id
}

impl OpenedPdfArchive {
    const KEY_XOBJECT: &'static [u8] = b"XObject";
    const KEY_SUBTYPE: &'static [u8] = b"Subtype";
    const KEY_MASK: &'static [u8] = b"Mask";

    const VALUE_SUBTYPE_IMAGE: &'static [u8] = b"Image";

    ///Wrap PDF document
    fn new(pdf_doc: PdfDocument) -> Self {
        OpenedPdfArchive {
            pages: pdf_doc.get_pages(),
            pdf_doc,
        }
    }

    ///Tries to get [ArchiveFile] from PDF page by [page_number]. May return None if where is no images on page
    fn file_by_page(&self, page_number: u32) -> PdfResult<Option<ArchiveFile>> {
        debug!(
            "Get PDF page resources at position: {}. Thread: {:?}",
            page_number,
            std::thread::current().name()
        );

        //pages in tne PDF is 1 based
        let page_resources = self.get_page_resources(
            *self
                .pages
                .get(&(page_number + 1))
                .expect("PDF page cannot be None"),
        );

        if let (Some(page_resources), _) = page_resources {
            let page_image = {
                //all image streams at the page
                let mut images_streams = page_resources
                    .get(Self::KEY_XOBJECT) //get XObject at the page
                    .and_then(|o| o.as_dict())?
                    .into_iter() //iterate over all XObject's child objects
                    .filter_map(|(bytes, object)| {
                        object
                            .as_reference() //get object reference and find it in the PDF
                            .and_then(|id| self.pdf_doc.get_object(id))
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
                            .transpose()
                    })
                    .collect::<Vec<_>>();

                if images_streams.len() == 1 {
                    let first_image = images_streams.remove(0);

                    let has_mask = first_image
                        .as_ref()
                        .map(|(_, stream)| stream.dict.has(Self::KEY_MASK));

                    //check is image has any mask. I decided to not support it now.
                    //For future implementation check jpeg2000 (PDF filer JPXDecode), JBIG2 (PDF filter JBIG2Decode)
                    if let Ok(true) = has_mask {
                        error!("Current version doesn't support PDF image masks");
                        Ok(None)
                    } else {
                        Some(first_image).transpose()
                    }
                } else {
                    error!(
                        "PDF page has unsupported images count: {}",
                        images_streams.len()
                    );
                    Ok(None)
                }
            }?;

            if let Some((page_name, page_stream)) = page_image {
                return Ok(Some(ArchiveFile {
                    pos: page_number as usize, //here we should to save zero based page position
                    name: format!("{}_{}", page_number, page_name), //a lot of PDF pages have equal names. So I add a page number
                    is_dir: false,
                    content: Some(page_stream.content.to_owned()),
                }));
            }
        }

        return Ok(None);
    }

    fn pages_count(&self) -> usize {
        self.pages.len()
    }
}

impl Deref for OpenedPdfArchive {
    type Target = PdfDocument;

    fn deref(&self) -> &Self::Target {
        &self.pdf_doc
    }
}

impl From<PdfDocument> for OpenedPdfArchive {
    fn from(pdf_doc: PdfDocument) -> Self {
        OpenedPdfArchive::new(pdf_doc)
    }
}

impl IntoIterator for OpenedPdfArchive {
    type Item = PdfResult<ArchiveFile>;
    type IntoIter = PdfArchiveIterator;

    fn into_iter(self) -> Self::IntoIter {
        PdfArchiveIterator::new(self)
    }
}

///Iterator over all PDF pages
struct PdfArchiveIterator {
    archive: OpenedPdfArchive,
    pos: u32,
}

impl PdfArchiveIterator {
    fn new(archive: OpenedPdfArchive) -> Self {
        PdfArchiveIterator { archive, pos: 0 }
    }
}

impl Iterator for PdfArchiveIterator {
    type Item = PdfResult<ArchiveFile>;

    fn next(&mut self) -> Option<Self::Item> {
        if (self.pos as usize) >= self.archive.pages_count() {
            return None;
        }

        let file_result = self
            .archive
            .file_by_page(self.pos)
            .and_then(|r| r.ok_or(Error::NotSupported));

        self.pos += 1;

        return Some(file_result);
    }
}

impl ExactSizeIterator for PdfArchiveIterator {
    fn len(&self) -> usize {
        self.archive.pages_count()
    }
}

#[cfg(test)]
pub mod tests {
    use super::*;
    use std::env;
    use std::fs::File;

    use lopdf;

    #[test]
    fn t() {
        let pdf_doc = {
            let mut path = env::current_dir().unwrap();

            path.push("test");
            path.push("archive");
            path.push("pdf");
            path.push("comic_test_3.pdf");

            lopdf::Document::load(path).unwrap()
        };

        let a = OpenedPdfArchive::new(pdf_doc);

        for s in a {
            let s = s.unwrap();

            println!("!!!!! {:?}", s);

            let c = s.content;

            let mut path = env::current_dir().unwrap();

            //            path.push(format!("{}.jpg", s.name));
            //
            //            std::fs::write(path, c.unwrap().as_slice());
        }
    }
}
