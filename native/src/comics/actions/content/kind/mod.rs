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

use std::collections::VecDeque;

use image::GenericImageView;

pub use error::*;

use crate::comics::container::ComicContainerFile;
use crate::comics::magic::MagicType;
use crate::comics::ml::{prepare_yolo_img, BoundingBox, PageYOLOInterpreter, YoloImg};
use crate::task::Task;

use self::comic_info::parse_comic_info;
pub use self::comic_info::{ComicInfo, ComicInfoPage};

mod comic_info;
mod error;

/// All possible variants of comic files
#[derive(Debug, Clone)]
pub enum ComicBookContent {
    /// Comic book page after object detection process
    ComicPage(ComicPage),
    ///Comic parsed metadata
    ComicInfo(ComicInfo),
}

impl From<ComicInfo> for ComicBookContent {
    fn from(comic_info: ComicInfo) -> Self {
        ComicBookContent::ComicInfo(comic_info)
    }
}

impl From<ComicPage> for ComicBookContent {
    fn from(page: ComicPage) -> Self {
        ComicBookContent::ComicPage(page)
    }
}

///Single comic book page with objects bounding boxes
#[derive(Clone, Debug)]
pub struct ComicPage {
    /// position in the archive.
    pub pos: usize,
    /// file name in the archive
    pub name: String,
    /// page width
    pub width: u32,
    /// page height
    pub height: u32,
    /// comic book page's bounding boxes
    pub b_boxes: Vec<BoundingBox>,
}

///Iterator which filter comic container files and return all supported variants
pub struct ComicBookContentIter<'a, I> {
    /// Inner iterator over all comic book container's files
    inner_iter: I,
    /// connected task
    task: &'a Task,
    /// ML Interpreter
    interpreter: &'a mut PageYOLOInterpreter,
    /// Comic book pages images buffer which capacity equals to [interpreter] input layer batch size
    yolo_img_buf: VecDeque<YoloImg>,
    page_wait_queue: queue::PageWaitQueue,
    /// Do we already parse comic book info file
    has_comic_info: bool,
}

impl<'a, I> ComicBookContentIter<'a, I> {
    /// Create new comic book container content iterator
    /// You can use provided [task] to stop emitting new items
    pub fn new(iter: I, task: &'a Task, interpreter: &'a mut PageYOLOInterpreter) -> Self {
        let batch_size = interpreter.input_dims().batch_size;

        ComicBookContentIter {
            inner_iter: iter,
            task,
            interpreter,
            yolo_img_buf: VecDeque::with_capacity(batch_size),
            page_wait_queue: queue::PageWaitQueue::with_capacity(batch_size),
            has_comic_info: false,
        }
    }

    /// Send [yolo_img_buf] into ML Interpreter to get objects bounding boxes
    fn process_batch(&mut self) {
        let current_batch_size = self.yolo_img_buf.len();

        // copy images into ML interpreter and get batched bounding boxes founded on the provided images above
        let batched_boxes_res = self
            .interpreter
            .copy_imgs(&mut self.yolo_img_buf)
            .and(self.interpreter.invoke());

        match batched_boxes_res {
            Err(e) => {
                // I don't know when ML can return Error.
                // Typically it shouldn't. Only in case of my incorrect usage.
                // Just in case, I set empty bounding boxes vector and allow use application as simple comic book viewer without smart part

                error!("Can't invoke ML Interpreter! Error: {}", e);

                // notify waiting queue that some pages should be finished without bounding boxes
                self.page_wait_queue
                    .fail_to_send_boxes(current_batch_size - self.yolo_img_buf.len());
            }
            Ok(mut batched_boxes) => {
                //check if we was sent full batch or not
                //If it wasn't full batch than take only that part of result bounding boxes
                //to prevent proceed empty boxes
                let box_iter = if current_batch_size >= batched_boxes.len() {
                    batched_boxes.drain(..)
                } else {
                    batched_boxes.drain(..current_batch_size)
                };

                // send bounding boxes to waiting queue
                self.page_wait_queue
                    .send_boxes_iter(box_iter, &self.interpreter.input_dims());
            }
        }
    }
}

impl<I> Iterator for ComicBookContentIter<'_, I>
where
    I: Iterator<Item = ComicContainerFile>,
{
    type Item = Result<ComicBookContent>;

    fn next(&mut self) -> Option<Self::Item> {
        // task was cancelled, cancel emitting as soon as possible
        if self.task.check().is_err() {
            return None;
        }

        if let Some(page) = self.page_wait_queue.get_finished_page() {
            // Return already processed pages if any
            return Some(Ok(page.into()));
        }

        while let Some(file) = self.inner_iter.next() {
            if is_comic_info_xml(&file.name, &file.content) {
                //we can have only one comic book info file...
                //Just in case if there are multiple into files (e.g. inside inner dirs)
                if !self.has_comic_info {
                    if let Some(comic_info) = parse_comic_info(file.content.as_slice()) {
                        self.has_comic_info = true;

                        return Some(Ok(comic_info.into()));
                    }
                }

                continue;
            } else if let Ok(img_format) = image::guess_format(&file.content) {
                // We will assume every image as comic book page
                match image::load_from_memory_with_format(&file.content, img_format) {
                    Err(err) => {
                        error!(
                            "Can't open comic book container image: '{}'. Error {}",
                            file.name, err
                        );

                        return Some(Err(err.into()));
                    }
                    Ok(comic_page_img) => {
                        let ComicContainerFile {
                            pos: file_pos,
                            name: file_name,
                            ..
                        } = file;

                        // comic book page without detected ML objects
                        let page = ComicPage {
                            pos: file_pos,
                            name: file_name,
                            width: comic_page_img.width(),
                            height: comic_page_img.height(),
                            b_boxes: vec![],
                        };

                        let yolo_input_dims = self.interpreter.input_dims();

                        // prepare YOLO input image
                        let yolo_img = prepare_yolo_img(comic_page_img, &yolo_input_dims);

                        self.page_wait_queue.push(page, yolo_img.len());

                        // add all created images into queue
                        self.yolo_img_buf.extend(yolo_img);

                        // if we have enough pages to send them to the ML interpreter
                        if self.yolo_img_buf.len() >= yolo_input_dims.batch_size {
                            //prevent long interpreter execution if task was cancelled
                            if self.task.check().is_err() {
                                return None;
                            }

                            self.process_batch();

                            if let Some(page) = self.page_wait_queue.get_finished_page() {
                                return Some(Ok(page.into()));
                            }
                        }
                    }
                }
            } else {
                error!(
                    "Comic book container contains unsupported file: '{}'",
                    file.name
                );

                return Some(Err(Error::NotSupported));
            }
        }

        if !self.yolo_img_buf.is_empty() {
            // lets process pages if we have some on non full capacity buffer
            self.process_batch();

            Ok(self.page_wait_queue.get_finished_page().map(Into::into)).transpose()
        } else {
            // we have reached the end of the comic book container
            None
        }
    }
}

/// Is provided file ComicInfo.xml with metadata
fn is_comic_info_xml(file_name: &str, file_content: &[u8]) -> bool {
    const COMIC_INFO_NAME: &str = "ComicInfo.xml";

    file_name == COMIC_INFO_NAME && MagicType::is_type(file_content, MagicType::XML)
}

mod queue {
    use std::collections::VecDeque;

    use crate::comics::ml::{collect_boxes, InputLayerDims};

    use super::{BoundingBox, ComicPage};

    pub struct PageWaitQueue {
        /// Queue of pages which didn't received all ML bounding boxes
        wait_queue: VecDeque<(ComicPage, usize)>,
        /// Queue of pages which finished waiting and ready to go
        done_queue: VecDeque<ComicPage>,
        /// Buffer of bathed bounding boxes for the most first page in the [wait_queue]
        page_boxes: Vec<Vec<BoundingBox>>,
    }

    impl PageWaitQueue {
        pub fn with_capacity(capacity: usize) -> Self {
            PageWaitQueue {
                wait_queue: VecDeque::with_capacity(capacity),
                done_queue: VecDeque::with_capacity(capacity),
                page_boxes: vec![],
            }
        }

        /// Add page to the waiting queue
        /// [batch_count] - how much batches of boxes this pages await to receive
        pub fn push(&mut self, page: ComicPage, batch_count: usize) {
            if batch_count == 0 {
                // page doesn't wait anything
                self.done_queue.push_back(page);
            } else {
                if self.wait_queue.is_empty() {
                    self.page_boxes.reserve_exact(batch_count);
                }

                self.wait_queue.push_back((page, batch_count));
            }
        }

        /// Send bounding boxes to waiting page queue
        /// Will panic if there is no pages in the queue
        pub fn send_boxes(&mut self, boxes: Vec<BoundingBox>, input_layer: &InputLayerDims) {
            let wait_over = {
                let (_, batch_count) = self
                    .wait_queue
                    .front()
                    .expect("Waiting pages queue is empty");

                self.page_boxes.push(boxes);

                self.page_boxes.len() == *batch_count
            };

            // If front page has received all boxes than collect them and send page to done_queue
            if wait_over {
                let (mut page, _) = self.wait_queue.pop_front().unwrap();

                collect_boxes(
                    &mut page.b_boxes,
                    self.page_boxes.drain(..),
                    page.width,
                    page.height,
                    input_layer,
                );

                self.done_queue.push_back(page);
            }
        }

        pub fn send_boxes_iter<T>(&mut self, boxes_iter: T, input_layer: &InputLayerDims)
        where
            T: IntoIterator<Item = Vec<BoundingBox>>,
        {
            for boxes in boxes_iter {
                self.send_boxes(boxes, input_layer);
            }
        }

        /// Get finished page if any
        pub fn get_finished_page(&mut self) -> Option<ComicPage> {
            self.done_queue.pop_front()
        }

        /// Call it than some batch cannot be send so some pages will be finished without bounding boxes
        pub fn fail_to_send_boxes(&mut self, failed_batch_count: usize) {
            if self.wait_queue.is_empty() {
                return;
            }

            let mut failed_batch_count = failed_batch_count;

            while failed_batch_count > 0 {
                // move paged to finished list while failed batch is not 0
                if let Some((page, batch_count)) = self.wait_queue.pop_front() {
                    self.done_queue.push_back(page);

                    // failed batch count can be less than waited batch be page
                    // We need to remove that page from waiting list anyway 'cause it will never get all boxes
                    if let Some(r) = failed_batch_count.checked_sub(batch_count) {
                        failed_batch_count = r;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
        }
    }

    #[cfg(test)]
    mod tests {
        use super::*;
        use crate::comics::ml::BoxClass;

        #[test]
        fn test_success_queue() {
            let mut q = PageWaitQueue::with_capacity(1);

            assert_eq!(q.done_queue.capacity(), 1);
            assert_eq!(q.wait_queue.capacity(), 1);
            assert_eq!(q.page_boxes.capacity(), 0);

            q.push(test_page(), 0);

            q.get_finished_page().expect("Should have done page");

            let batch_size = 2;

            q.push(test_page(), batch_size);

            assert_eq!(q.wait_queue.len(), 1);
            assert_eq!(q.done_queue.len(), 0);

            let input_layer = test_input_layer(batch_size);

            q.send_boxes(vec![test_bounding_box()], &input_layer);

            assert_eq!(q.wait_queue.len(), 1);
            assert_eq!(q.done_queue.len(), 0);

            q.send_boxes(vec![test_bounding_box()], &input_layer);

            assert_eq!(q.wait_queue.len(), 0);
            assert_eq!(q.done_queue.len(), 1);

            let page = q.get_finished_page().expect("Should have finished page");

            assert_eq!(page.b_boxes.len(), 2);
        }

        #[test]
        fn test_fail_to_send() {
            let mut q = PageWaitQueue::with_capacity(3);

            q.fail_to_send_boxes(1);

            q.push(test_page(), 3);
            q.push(test_page(), 2);

            q.fail_to_send_boxes(2);

            assert_eq!(q.done_queue.len(), 1);

            q.fail_to_send_boxes(2);

            assert_eq!(q.done_queue.len(), 2);
        }

        fn test_page() -> ComicPage {
            ComicPage {
                pos: 0,
                name: "test".to_string(),
                width: 3975,
                height: 3056,
                b_boxes: vec![],
            }
        }

        fn test_bounding_box() -> BoundingBox {
            BoundingBox {
                y_min: 0.1,
                y_max: 0.2,
                x_min: 0.1,
                x_max: 0.2,
                prob: 0.99,
                class: BoxClass::SpeechBalloon,
            }
        }

        fn test_input_layer(batch_size: usize) -> InputLayerDims {
            InputLayerDims {
                batch_size,
                width: 480,
                height: 736,
                channels: 3,
            }
        }
    }
}
