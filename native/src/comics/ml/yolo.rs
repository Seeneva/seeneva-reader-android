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
use std::convert::TryInto;

use image::RgbImage;
use ndarray::prelude::*;
use rayon::prelude::*;

use tflite_rs::{error::Result, Interpreter, Tensor};

/// Wrapper of the YOLO comic book page interpreter
#[derive(Debug)]
pub struct PageYOLOInterpreter(Interpreter);

impl PageYOLOInterpreter {
    /// Input layer should be length equal to this value
    const INPUT_DIMS: usize = 4;
    /// How much output tensors should be
    const OUTPUT_TENSORS_COUNT: u32 = 3;

    /// Wrap tensorflow lite interpreter
    pub fn new(interpreter: Interpreter) -> Self {
        PageYOLOInterpreter(interpreter)
    }

    /// Get input layer dimensions
    pub fn input_dims(&self) -> InputLayerDims {
        let input_tensor = self.input_tensor();

        assert_eq!(
            input_tensor.dims_size(),
            Self::INPUT_DIMS,
            "Invalid input dims size: {}",
            input_tensor.dims_size()
        );

        fn build_input_layer_dims(tensor: &Tensor) -> Option<InputLayerDims> {
            Some(InputLayerDims {
                batch_size: tensor.dim(0)?,
                width: tensor.dim(2)? as _,
                height: tensor.dim(1)? as _,
                channels: tensor.dim(3)? as _,
            })
        }

        build_input_layer_dims(&input_tensor).expect("Can't get interpreter input dims")
    }

    /// Copy images into interpreter
    /// [imgs] count should be less or equal to input layer batch size
    /// If [imgs] count is less than it will be padded using empty images
    pub fn copy_imgs<T>(&self, imgs: T) -> Result<()>
    where
        T: YoloImgContainer,
    {
        let input_dims = self.input_dims();

        let input_len = imgs.len();

        // we should add empty images before send it to Tensorflow interpreter
        let pad_iter = if input_len < input_dims.batch_size {
            Some(rayon::iter::repeatn(
                0f32,
                input_dims.height as usize
                    * input_dims.width as usize
                    * input_dims.channels as usize
                    * (input_dims.batch_size - input_len),
            ))
        } else {
            None
        };

        // preprocess each image by resizing and normalize
        let imgs: Vec<_> = imgs
            .par_drain(..input_dims.batch_size.min(input_len))
            .flat_map(|img| {
                let img = img.into_inner();

                assert!(
                    img.width() == input_dims.width && img.height() == input_dims.height,
                    "Invalid image shape: ({}x{})",
                    img.width(),
                    img.height()
                );

                img.into_raw()
            })
            // normalise every pixel component
            .map(|v| v as f32 / u8::MAX as f32)
            .chain(pad_iter.into_par_iter().flatten())
            .collect();

        self.input_tensor().copy_from_buffer(imgs)
    }

    /// Runs inference
    pub fn invoke(&mut self) -> Result<Vec<Vec<BoundingBox>>> {
        self.0.invoke()?;

        assert_eq!(
            self.0.output_tensors_count(),
            Self::OUTPUT_TENSORS_COUNT,
            "Invalid output tensor count size: {}",
            self.0.output_tensors_count()
        );

        // f32 relative coordinates of boxes (y_min, x_min, y_max, x_max) and its probability.
        // Shape: (batch_size, max_output_size, 5)
        let scored_boxes = self.0.output_tensor(0).unwrap();
        // i32 represent i-th box class id
        // Shape: (batch_size, max_output_size, 1)
        let box_classes = self.0.output_tensor(1).unwrap();
        // i32 how many valid boxes was found on i-th image.
        // Shape: (batch_size, 1)
        let valid_detections = self.0.output_tensor(2).unwrap();

        let scored_boxes_array = scored_boxes
            .data::<f32>()
            .and_then(|data| {
                let shape: [usize; 3] = scored_boxes.dims().try_into().ok()?;

                ArrayView3::from_shape(shape, data).ok()
            })
            .expect("Can't get scored boxes array");

        let box_classes_array = box_classes
            .data::<i32>()
            .and_then(|data| {
                let shape: [usize; 2] = box_classes.dims().try_into().ok()?;

                ArrayView2::from_shape(shape, data).ok()
            })
            .expect("Can't get box classes array");

        let valid_detections_array = valid_detections
            .data::<i32>()
            .and_then(|data| {
                let shape: [usize; 1] = valid_detections.dims().try_into().ok()?;

                ArrayView1::from_shape(shape, data).ok()
            })
            .expect("Can't get valid detections array");

        //range equal to batch
        //vector with batched boxes
        let batched_boxes: Vec<_> = (0..scored_boxes_array.len_of(Axis(0)))
            .into_par_iter()
            .map(|batch_id| {
                //get number of valid box for current batch_id
                let valid_count = valid_detections_array.slice(s![batch_id]).into_scalar();

                let boxes: Vec<_> = scored_boxes_array
                    //slice and iterate over each scored box for provided batch_id
                    .slice(s![batch_id, .., ..])
                    .outer_iter()
                    .into_par_iter()
                    // zip each box with it class_id
                    .zip_eq(
                        box_classes_array
                            .slice(s![batch_id, ..])
                            .outer_iter()
                            .into_par_iter(),
                    )
                    //take only first valid boxes and ignore others
                    .take(*valid_count as _)
                    .map(|(box_v, class_id)| BoundingBox {
                        y_min: box_v[0],
                        x_min: box_v[1],
                        y_max: box_v[2],
                        x_max: box_v[3],
                        prob: box_v[4],
                        class: (*class_id.into_scalar() as u32).into(),
                    })
                    .collect();

                boxes
            })
            .collect();

        Ok(batched_boxes)
    }

    /// Get single input tensor
    fn input_tensor(&self) -> Tensor {
        self.0
            .input_tensor(0)
            .expect("Comic page YOLO model should have single input tensor")
    }
}

impl From<Interpreter> for PageYOLOInterpreter {
    fn from(interpreter: Interpreter) -> Self {
        Self::new(interpreter)
    }
}

/// Describes input layer
#[derive(Debug, Copy, Clone)]
pub struct InputLayerDims {
    pub batch_size: usize,
    pub width: u32,
    pub height: u32,
    pub channels: u32,
}

/// Single bounding box returned by ML model
#[derive(Debug, Copy, Clone)]
pub struct BoundingBox {
    /// minimum y coordinate of the box
    pub y_min: f32,
    /// minimum x coordinate of the box
    pub x_min: f32,
    /// maximum y coordinate of the box
    pub y_max: f32,
    /// maximum x coordinate of the box
    pub x_max: f32,
    /// probability of the box
    pub prob: f32,
    /// type of the box
    pub class: BoxClass,
}

impl BoundingBox {
    /// Return absolute values of x_min and x_max
    pub fn x_absolute(&self, src_width: u32) -> (f32, f32) {
        (
            Self::absolute(self.x_min, src_width),
            Self::absolute(self.x_max, src_width),
        )
    }

    /// Return absolute values of y_min and y_max
    pub fn y_absolute(&self, src_height: u32) -> (f32, f32) {
        (
            Self::absolute(self.y_min, src_height),
            Self::absolute(self.y_max, src_height),
        )
    }

    fn absolute(rel: f32, full: u32) -> f32 {
        rel * full as f32
    }
}

/// Different supported types of bounding boxes
#[derive(Debug, Copy, Clone)]
#[repr(u32)]
pub enum BoxClass {
    /// Single speech balloon on comic book page
    SpeechBalloon = 0,
    /// Single panel on comic book page
    Panel = 1,
}

// converts from class it to the struct
impl From<u32> for BoxClass {
    fn from(id: u32) -> Self {
        match id {
            t if t == Self::SpeechBalloon as _ => Self::SpeechBalloon,
            t if t == Self::Panel as _ => Self::Panel,
            _ => panic!("Unsupported box class id: {}", id),
        }
    }
}

#[derive(Debug, Clone)]
pub struct YoloImg(RgbImage);

impl YoloImg {
    pub(super) fn new(img: RgbImage) -> Self {
        YoloImg(img)
    }

    pub fn into_inner(self) -> RgbImage {
        self.0
    }
}

pub trait YoloImgContainer: ParallelDrainRange<Item = YoloImg> {
    /// length of the container
    fn len(&self) -> usize;
}

impl YoloImgContainer for &mut VecDeque<YoloImg> {
    fn len(&self) -> usize {
        VecDeque::len(self)
    }
}

impl YoloImgContainer for &mut Vec<YoloImg> {
    fn len(&self) -> usize {
        Vec::len(self)
    }
}
