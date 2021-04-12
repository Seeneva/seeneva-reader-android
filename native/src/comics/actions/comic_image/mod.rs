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

use std::io::Cursor;

use image;
use image::imageops::FilterType as ImageFilterType;
use image::GenericImageView;
use rayon::prelude::*;

use crate::comics::container::{ComicContainer, ComicContainerFile, ComicContainerVariant};
use crate::task::{CancelledError, Task};

pub use self::error::*;

pub mod error;

///data, width, height
pub type OpenedImage = (Vec<u8>, u32, u32);
///width, height, fast_resizing
pub type ResizeParams = Option<(u32, u32, bool)>;
/// (x_start, y_start, width, height)
pub type CropParams = Option<(u32, u32, u32, u32)>;

/// Supported color models
#[derive(Debug, Copy, Clone)]
pub enum ColorModel {
    /// 32 bit color
    RGBA8888,
    /// 16 bit color
    RGB565,
}

/// Get raw page image at [pos] without decode it
pub fn get_comic_book_raw_page(
    task: &Task,
    container: &mut ComicContainer,
    pos: usize,
) -> Result<Option<OpenedImage>, GetComicRawImageError> {
    match container.file_at(pos)? {
        Some(ComicContainerFile {
            content: page_buf, ..
        }) => {
            task.check()?;

            // Get proper page img dimensions
            let (w, h) = image::io::Reader::new(Cursor::new(&page_buf))
                .with_guessed_format()
                .map_err(image::error::ImageError::from)
                .and_then(image::io::Reader::into_dimensions)?;

            task.check()?;

            Ok(Some((page_buf, w, h)))
        }
        None => Ok(None),
    }
}

/// * Crop and resize src image
pub fn resize_comic_book_page(
    task: &Task,
    img: &image::DynamicImage,
    color_model: ColorModel,
    resize: ResizeParams,
    crop: CropParams,
) -> Result<Vec<u8>, ResizeComicImageError> {
    let mut modified_img: Option<image::DynamicImage> = None;

    // apply crop params
    if let Some((x, y, width, height)) =
        crop.filter(|(_, _, width, height)| *width != img.width() || *height != img.height())
    {
        debug!(
            "Crop image. Source: ({} {}). Crop: (x: {} y: {} w: {} h: {})",
            img.width(),
            img.height(),
            x,
            y,
            width,
            height
        );

        modified_img = img.crop_imm(x, y, width, height).into();
    }

    task.check()?;

    //apply resize params
    if let Some((img, width, height, filter_type)) =
        modified_img.as_ref().or(Some(img)).and_then(|img| {
            resize
                .filter(|(width, height, _)| *width != img.width() || *height != img.height())
                .map(|(w, h, resize_fast)| {
                    (
                        img,
                        w,
                        h,
                        if resize_fast {
                            ImageFilterType::Nearest
                        } else {
                            ImageFilterType::Triangle
                        },
                    )
                })
        })
    {
        debug!(
            "Resize image. From: ({} {}). To: ({} {}). Filter type {:?}",
            img.width(),
            img.height(),
            width,
            height,
            filter_type
        );

        modified_img = img.resize_exact(width, height, filter_type).into();
    }

    task.check()?;

    let buf = match color_model {
        ColorModel::RGBA8888 => modified_img
            .map_or_else(|| img.to_rgba8(), |img| img.into_rgba8())
            .into_raw(),
        ColorModel::RGB565 => {
            let mut img = modified_img.map_or_else(|| img.to_rgb8(), |img| img.into_rgb8());

            image::imageops::dither(&mut img, &Rgb565ColorMap);

            img.into_raw()
                .into_par_iter()
                .chunks(3)
                .map(|pixel_buf| {
                    if let Err(self::CancelledError) = task.check() {
                        None
                    } else {
                        Some(pixel_buf)
                    }
                })
                .while_some()
                .flat_map_iter(|mut pixel_buf| {
                    // Convert RGB888 pixels into RGB565
                    // How it works: http://www.barth-dev.de/online/rgb565-color-picker/
                    let rgb565 = (u16::from(pixel_buf[0] & 0b11111000) << 8
                        | u16::from(pixel_buf[1] & 0b11111100) << 3
                        | u16::from(pixel_buf[2] >> 3))
                    .to_ne_bytes();

                    pixel_buf.clear();

                    pixel_buf.extend_from_slice(&rgb565);

                    pixel_buf
                })
                .collect::<Vec<_>>()
        }
    };

    task.check()?;

    Ok(buf)
}

/// Map RGB to RGB565 colors for the Floyd-Steinberg dithering
struct Rgb565ColorMap;

impl image::imageops::colorops::ColorMap for Rgb565ColorMap {
    type Color = image::Rgb<u8>;

    fn index_of(&self, _: &Self::Color) -> usize {
        unreachable!()
    }

    fn map_color(&self, color: &mut Self::Color) {
        color[0] = color[0] & 0b11110000;
        color[1] = color[1] & 0b11110000;
        color[2] = color[2] & 0b11110000;
    }
}
