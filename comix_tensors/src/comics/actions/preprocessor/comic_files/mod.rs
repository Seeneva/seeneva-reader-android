mod comic_info;
mod comic_page;

use self::comic_info::*;
use self::comic_page::*;
use super::error::ComicPreprocessError;
use super::{ArchiveFile, MagicType};
use crate::{ComicFile, ComicInfo, InterpreterInShape, InterpreterInput};

use image::guess_format as guess_image_format;
use tokio::prelude::*;

const COMIC_INFO_NAME: &str = "ComicInfo.xml";

impl From<InterpreterInput> for ComicFile {
    fn from(input: InterpreterInput) -> Self {
        ComicFile::Image(input)
    }
}

impl From<ComicInfo> for ComicFile {
    fn from(comic_info: ComicInfo) -> Self {
        ComicFile::ComicInfo(comic_info)
    }
}

/// Stream which filter comics files from archive and prepare them for TF model
#[derive(Debug)]
pub struct PreprocessComicFileStream<S> {
    stream: S,
    shape: InterpreterInShape,
    input_items: Option<Vec<InterpreterSingleInput>>,
}

impl<S> PreprocessComicFileStream<S>
where
    S: Stream<Item = ArchiveFile>,
{
    pub fn new(stream: S, shape: InterpreterInShape) -> Self {
        PreprocessComicFileStream {
            stream,
            shape,
            input_items: None,
        }
    }

    ///Add interpreter_input to the collection. If the collection size is equal to batch size,
    /// than method will return single batched [InterpreterInput]
    fn add_interpreter_input(
        &mut self,
        interpreter_input: InterpreterSingleInput,
    ) -> Option<InterpreterInput> {
        //set the capacity equal to batch size of the TF model input
        let input_items = self
            .input_items
            .get_or_insert(Vec::with_capacity(self.shape.batch_size() as usize));

        input_items.push(interpreter_input);

        //Check file count and batch them if needed
        if input_items.len() == input_items.capacity() {
            self.batch_all_pending_inputs()
        } else {
            None
        }
    }

    ///Batch all current added inputs
    fn batch_all_pending_inputs(&mut self) -> Option<InterpreterInput> {
        self.input_items.take().map(into_batched_interpreter_input)
    }
}

impl<S, E> Stream for PreprocessComicFileStream<S>
where
    E: From<ComicPreprocessError>,
    S: Stream<Item = ArchiveFile, Error = E>,
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
                    let ArchiveFile {
                        pos, name, content, ..
                    } = file;

                    //File should have the content. Or it is the app error
                    let content = content.expect("File doesn't have any content");

                    if is_image(content.as_slice()) {
                        debug!("Trying to prepare archive file on position {} '{}' as interpreter input", pos, name);
                        let input = single_interpreter_input(
                            content.as_slice(),
                            name.as_str(),
                            self.shape,
                        )?;

                        debug!("Interpreter input prepared from image file");

                        let input = InterpreterSingleInput::new(pos, name, input);

                        if let Some(batched_input) = self.add_interpreter_input(input) {
                            return Ok(Async::Ready(Some(batched_input.into())));
                        }
                    } else if is_comic_info_xml(name.as_str(), content.as_slice()) {
                        debug!(
                            "Trying to parse archive file on position {} '{}' as ComicInfo",
                            pos, name
                        );

                        if let Some(comic_info) = parse_comic_info(content.as_slice()) {
                            debug!("ComicInfo parsed");
                            return Ok(Async::Ready(Some(comic_info.into())));
                        }
                    }
                }
                None => match self.batch_all_pending_inputs() {
                    Some(batched_input) => {
                        return Ok(Async::Ready(Some(ComicFile::Image(batched_input))));
                    }
                    None => {
                        return Ok(Async::Ready(None));
                    }
                },
            }

            //return Ok(Async::NotReady);
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
