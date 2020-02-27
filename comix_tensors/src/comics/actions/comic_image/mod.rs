use image;
use tokio::prelude::*;

use crate::comics::archive::ArchiveFile;

pub use self::error::GetComicImageError;

mod error;

///RGBA, width, height
pub type OpenedImage = (Vec<u8>, u32, u32);
type ResizeParams = (u32, u32);

///Future which returns comic RGBA content
pub struct GetComicImageFuture<F> {
    state: State<F>,
    resize: Option<ResizeParams>,
}

impl<F> GetComicImageFuture<F> {
    ///[future] - [Future] of [ArchiveFile].
    pub fn new(future: F, resize: Option<ResizeParams>) -> Self {
        GetComicImageFuture {
            state: State::FindImage(future),
            resize,
        }
    }
}

enum State<F> {
    FindImage(F),
    ExtractImage(Vec<u8>),
}

impl<F, E> Future for GetComicImageFuture<F>
where
    F: Future<Item = Option<ArchiveFile>, Error = E>,
    E: From<GetComicImageError>,
{
    type Item = OpenedImage;
    type Error = F::Error;

    fn poll(&mut self) -> Poll<Self::Item, Self::Error> {
        match self.state {
            State::FindImage(ref mut future) => match try_ready!(future.poll()) {
                Some(archive_file) => match archive_file.content {
                    Some(img_content) => {
                        self.state = State::ExtractImage(img_content);
                        task::current().notify();
                        Ok(Async::NotReady)
                    }
                    None => Err(GetComicImageError::EmptyFile.into()),
                },
                None => Err(GetComicImageError::CantFind.into()),
            },
            State::ExtractImage(ref image_buf) => image::load_from_memory(image_buf)
                .map(|img| {
                    let img = resize_image(img, &self.resize).to_rgba();
                    let w = img.width();
                    let h = img.height();
                    let rgba_buf = img.into_raw();
                    Async::Ready((rgba_buf, w, h))
                })
                .map_err(|e| GetComicImageError::OpenError(e).into()),
        }
    }
}

///resize image if needed
fn resize_image(img: image::DynamicImage, resize: &Option<ResizeParams>) -> image::DynamicImage {
    match resize {
        Some((width, height)) => img.thumbnail(*width, *height),
        None => img,
    }
}
