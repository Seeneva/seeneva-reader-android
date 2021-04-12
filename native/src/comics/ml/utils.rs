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

use image::imageops::{crop_imm, thumbnail};
use image::{DynamicImage, GenericImage, GenericImageView, Rgb, RgbImage};
use itertools::Itertools;
use rayon::prelude::*;

use super::{BoundingBox, InputLayerDims, YoloImg};

/// Prepare provided image as YOLO input image.
/// May slice the image into small pieces in case if image was wide.
pub fn prepare_yolo_img(page: DynamicImage, input_layer: &InputLayerDims) -> PreparedYoloImg {
    // For now interpreter supports only 3 channels images (RGB)
    let page = page.into_rgb8();

    //YOLO input size
    let yolo_width = input_layer.width;
    let yolo_height = input_layer.height;

    //Just use the image if it is already has a proper size
    if page.width() == yolo_width && page.height() == yolo_height {
        return page.into();
    }

    //If image is smaller than YOLO input size. Add padding to the image
    if page.width() < yolo_width && page.height() < yolo_height {
        return copy_top_left(&page, yolo_width, yolo_height).into();
    }

    // If comic book page is wide, it is better to split it into small vertical pieces
    let (slices_count, slice_width) = slice_params(page.width(), page.height());

    debug!(
        "YOLO input image should be sliced into {} pieces. Width of the single piece: {}",
        slices_count, slice_width
    );

    if slices_count > 1 {
        let mut slices = Vec::with_capacity(slices_count as _);

        (0..slices_count)
            .into_par_iter()
            .map(|i| {
                let x = slice_width * i;

                //crop slice from the image
                crop_imm(&page, x, 0, slice_width, page.height())
            })
            .map(|cropped_page| resize_yolo_img(&cropped_page, yolo_width, yolo_height))
            .collect_into_vec(&mut slices);

        slices.into()
    } else {
        resize_yolo_img(&page, yolo_width, yolo_height).into()
    }
}

/// Collect YOLO bounding boxes from [src], convert them from small YOLO input image
/// to full sized source image with size [img_full_width] and [img_full_height],
/// and put them into [target]
pub fn collect_boxes<E, T, B>(
    target: &mut E,
    src: T,
    img_full_width: u32,
    img_full_height: u32,
    input_layer: &InputLayerDims,
) where
    E: Extend<BoundingBox>,
    T: IntoIterator<Item = B>,
    B: IntoIterator<Item = BoundingBox>,
{
    //YOLO input size which can have optional paddings to preserve input image aspect ratio
    let yolo_width = input_layer.width;
    let yolo_height = input_layer.height;

    let src = src
        .into_iter()
        .zip_eq({
            let (slice_count, slice_width) = slice_params(img_full_width, img_full_height);

            (0..slice_count).map(move |slice_number| {
                let left_slices_width = slice_width * slice_number;

                let slice_width = if slice_number == slice_count - 1 {
                    // last slice can have less width than others
                    img_full_width - left_slices_width
                } else {
                    // other slices should have equal width
                    slice_width
                };

                (left_slices_width, slice_width)
            })
        })
        .flat_map(
            |(slice_boxes, (left_slices_width, slice_width))| {
                slice_boxes.into_iter().map(move |mut b_box| {
                    // get real yolo input image size without padding
                    let (real_yolo_width, real_yolo_height) =
                        resize_dimensions(slice_width, img_full_height, yolo_width, yolo_height);

                    // get box X absolute value relative to YOLO input image with optional paddings
                    let (x_min, x_max) = b_box.x_absolute(yolo_width);

                    // convert absolute box value into absolute value relative to full sized image
                    // can have optional X shift:
                    //
                    // if it is not first source image slice
                    // than we should add left slice width to the current bounding boxes
                    // cause we stick slices together
                    // =========   =========
                    // |       |   |       |
                    // |w = 100|   |x_min  |
                    // |       |   |+= 100 |
                    // =========   =========
                    let convert_into_full =
                        |val: f32, size: u32, new_full_size: u32, shift: u32| {
                            val / size as f32 * new_full_size as f32 + shift as f32
                        };

                    // convert X and Y coordinates from small YOLO input image to full sized src image
                    // Also it helps to put together slices
                    b_box.x_min =
                        convert_into_full(x_min, real_yolo_width, slice_width, left_slices_width)
                            / img_full_width as f32;

                    b_box.x_max =
                        convert_into_full(x_max, real_yolo_width, slice_width, left_slices_width)
                            / img_full_width as f32;

                    // get box Y absolute value relative to YOLO input image with optional paddings
                    let (y_min, y_max) = b_box.y_absolute(yolo_height);

                    b_box.y_min = convert_into_full(y_min, real_yolo_height, img_full_height, 0)
                        / img_full_height as f32;

                    b_box.y_max = convert_into_full(y_max, real_yolo_height, img_full_height, 0)
                        / img_full_height as f32;

                    b_box
                })
            },
        );

    target.extend(src);
}

/// Return how many slices should be generated from input image size and single slice width
/// Last slice can have less width
fn slice_params(width: u32, height: u32) -> (u32, u32) {
    if width > height {
        // How many slices will be
        let slices_count = (width as f32 / height as f32).ceil() as u32;

        // single slice width (+- 1 pixel)
        let slice_width = (width as f32 / slices_count as f32).round() as u32;

        (slices_count, slice_width)
    } else {
        (1, width)
    }
}

/// Resize comic book RGB page into provided YOLO input [width] and [height]
/// Aspect ratio of the page will be preserved by adding optional padding
fn resize_yolo_img<I>(page: &I, width: u32, height: u32) -> YoloImg
where
    I: GenericImageView<Pixel = Rgb<u8>>,
{
    debug!(
        "Resize input {}x{} to YOLO input size",
        page.width(),
        page.height()
    );

    let page = {
        let (width, height) = resize_dimensions(page.width(), page.height(), width, height);

        // resize with preserver aspect ratio
        thumbnail(page, width, height)
    };

    // if image size not equals to YOLO size we should add padding to the image
    let page = if page.width() != width || page.height() != height {
        copy_top_left(&page, width, height)
    } else {
        page
    };

    YoloImg::new(page).into()
}

/// Helper to preserve image ration. Taken from image crate
fn resize_dimensions(width: u32, height: u32, nwidth: u32, nheight: u32) -> (u32, u32) {
    let ratio = u64::from(width) * u64::from(nheight);
    let nratio = u64::from(nwidth) * u64::from(height);

    let use_width = nratio <= ratio;

    let intermediate = if use_width {
        u64::from(height) * u64::from(nwidth) / u64::from(width)
    } else {
        u64::from(width) * u64::from(nheight) / u64::from(height)
    };

    if use_width {
        if intermediate <= u64::from(::std::u32::MAX) {
            (nwidth, intermediate as u32)
        } else {
            (
                (u64::from(nwidth) * u64::from(::std::u32::MAX) / intermediate) as u32,
                ::std::u32::MAX,
            )
        }
    } else if intermediate <= u64::from(::std::u32::MAX) {
        (intermediate as u32, nheight)
    } else {
        (
            ::std::u32::MAX,
            (u64::from(nheight) * u64::from(::std::u32::MAX) / intermediate) as u32,
        )
    }
}

/// Copy [src] image into new image with specific size [width] and [height]
fn copy_top_left<S>(src: &S, width: u32, height: u32) -> RgbImage
where
    S: GenericImageView<Pixel = Rgb<u8>>,
{
    debug!(
        "Src image {}x{} will be copied into {}x{}",
        src.width(),
        src.height(),
        width,
        height
    );

    let mut img = RgbImage::new(width, height);

    img.copy_from(src, 0, 0).unwrap();

    img
}

#[derive(Debug, Clone)]
pub enum PreparedYoloImg {
    Normal(YoloImg),
    /// Source image was wide so it was sliced into vertical pieces ready for Yolo interpreter
    Wide(Vec<YoloImg>),
}

impl PreparedYoloImg {
    pub fn len(&self) -> usize {
        match self {
            Self::Normal(_) => 1,
            Self::Wide(imgs) => imgs.len(),
        }
    }
}

impl From<YoloImg> for PreparedYoloImg {
    fn from(img: YoloImg) -> Self {
        Self::Normal(img)
    }
}

impl From<Vec<YoloImg>> for PreparedYoloImg {
    fn from(imgs: Vec<YoloImg>) -> Self {
        Self::Wide(imgs)
    }
}

impl From<RgbImage> for PreparedYoloImg {
    fn from(img: RgbImage) -> Self {
        YoloImg::new(img).into()
    }
}

#[derive(Debug)]
pub enum PreparedYoloImgIntoIter {
    Normal(std::iter::Once<YoloImg>),
    Wide(<Vec<YoloImg> as IntoIterator>::IntoIter),
}

impl Iterator for PreparedYoloImgIntoIter {
    type Item = YoloImg;

    fn next(&mut self) -> Option<Self::Item> {
        match self {
            Self::Normal(img_iter) => img_iter.next(),
            Self::Wide(imgs_iter) => imgs_iter.next(),
        }
    }

    fn size_hint(&self) -> (usize, Option<usize>) {
        match self {
            Self::Normal(iter) => iter.size_hint(),
            Self::Wide(imgs_iter) => imgs_iter.size_hint(),
        }
    }
}

impl ExactSizeIterator for PreparedYoloImgIntoIter {}

impl IntoIterator for PreparedYoloImg {
    type Item = YoloImg;
    type IntoIter = PreparedYoloImgIntoIter;

    fn into_iter(self) -> Self::IntoIter {
        match self {
            Self::Normal(img) => PreparedYoloImgIntoIter::Normal(std::iter::once(img)),
            Self::Wide(imgs) => PreparedYoloImgIntoIter::Wide(imgs.into_iter()),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::comics::ml::BoxClass;

    const DEFAULT_YOLO_W: u32 = 480;
    const DEFAULT_YOLO_H: u32 = 736;

    #[test]
    fn test_prepare_small_img() {
        match prepare_base(300, 300, DEFAULT_YOLO_W, DEFAULT_YOLO_H) {
            PreparedYoloImg::Normal(img) => {
                let img = img.into_inner();

                assert!(img.width() == DEFAULT_YOLO_W && img.height() == DEFAULT_YOLO_H);
            }
            _ => panic!("Non single image"),
        }
    }

    #[test]
    fn test_prepare_equal_img() {
        match prepare_base(
            DEFAULT_YOLO_W,
            DEFAULT_YOLO_H,
            DEFAULT_YOLO_W,
            DEFAULT_YOLO_H,
        ) {
            PreparedYoloImg::Normal(img) => {
                let img = img.into_inner();

                assert!(img.width() == DEFAULT_YOLO_W && img.height() == DEFAULT_YOLO_H);
            }
            _ => panic!("Non single image"),
        }
    }

    #[test]
    fn test_prepare_wide_img() {
        match prepare_base(3806, 1600, DEFAULT_YOLO_W, DEFAULT_YOLO_H) {
            PreparedYoloImg::Wide(imgs) => {
                assert_eq!(imgs.len(), 3);

                for img in imgs {
                    let img = img.into_inner();

                    assert!(img.width() == DEFAULT_YOLO_W && img.height() == DEFAULT_YOLO_H);
                }
            }
            _ => panic!("Non multiple images"),
        }
    }

    #[test]
    fn test_collect_boxes() {
        let mut target = vec![];

        let src = vec![
            vec![
                BoundingBox {
                    y_min: 0.10113741,
                    x_min: 0.032201152,
                    y_max: 0.15527679,
                    x_max: 0.15850648,
                    prob: 0.9991115,
                    class: BoxClass::SpeechBalloon,
                },
                BoundingBox {
                    y_min: 0.7433123,
                    x_min: 0.4329071,
                    y_max: 0.79494464,
                    x_max: 0.5628415,
                    prob: 0.99908966,
                    class: BoxClass::SpeechBalloon,
                },
                BoundingBox {
                    y_min: 0.6662415,
                    x_min: 0.8015173,
                    y_max: 0.7118095,
                    x_max: 0.8876936,
                    prob: 0.9982616,
                    class: BoxClass::SpeechBalloon,
                },
                BoundingBox {
                    y_min: 0.72359306,
                    x_min: 0.7819696,
                    y_max: 0.7596629,
                    x_max: 0.87533325,
                    prob: 0.9981188,
                    class: BoxClass::SpeechBalloon,
                },
            ],
            vec![
                BoundingBox {
                    y_min: 0.59298694,
                    x_min: 0.14427133,
                    y_max: 0.6527486,
                    x_max: 0.2648035,
                    prob: 0.9989871,
                    class: BoxClass::SpeechBalloon,
                },
                BoundingBox {
                    y_min: 0.85487705,
                    x_min: 0.8615609,
                    y_max: 0.882177,
                    x_max: 0.93318987,
                    prob: 0.9989372,
                    class: BoxClass::SpeechBalloon,
                },
                BoundingBox {
                    y_min: 0.882211,
                    x_min: 0.84584534,
                    y_max: 0.9136155,
                    x_max: 0.94628966,
                    prob: 0.9988945,
                    class: BoxClass::SpeechBalloon,
                },
                BoundingBox {
                    y_min: 0.6501723,
                    x_min: 0.24025142,
                    y_max: 0.68858004,
                    x_max: 0.33495066,
                    prob: 0.99576735,
                    class: BoxClass::SpeechBalloon,
                },
                BoundingBox {
                    y_min: 0.57451844,
                    x_min: 0.5800116,
                    y_max: 0.63073516,
                    x_max: 0.70515716,
                    prob: 0.9697446,
                    class: BoxClass::SpeechBalloon,
                },
            ],
        ];

        let src_box_count = src.iter().map(|v| v.len()).sum();

        collect_boxes(
            &mut target,
            src,
            3975,
            3056,
            &yolo_input_layer(DEFAULT_YOLO_W, DEFAULT_YOLO_H),
        );

        assert_eq!(target.len(), src_box_count);
    }

    fn prepare_base(src_w: u32, src_h: u32, yolo_w: u32, yolo_h: u32) -> PreparedYoloImg {
        prepare_yolo_img(
            DynamicImage::new_rgb8(src_w, src_h),
            &yolo_input_layer(yolo_w, yolo_h),
        )
    }

    fn yolo_input_layer(width: u32, height: u32) -> InputLayerDims {
        InputLayerDims {
            batch_size: 3,
            width,
            height,
            channels: 3,
        }
    }
}
