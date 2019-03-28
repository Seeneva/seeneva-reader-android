///Module to prepare comic pages as TF model input
use super::{ComicPreprocessError, InterpreterInShape, InterpreterInput};

use image;
use ndarray::prelude::*;
use ndarray::{IntoDimension, RemoveAxis};

///Describe single input item of Tensorflow model
#[derive(Debug, Clone)]
pub struct InterpreterSingleInput {
    pos: usize,
    name: String,
    content: Array3<f32>,
}

impl InterpreterSingleInput {
    pub fn new(pos: usize, name: String, content: Array3<f32>) -> Self {
        InterpreterSingleInput { pos, name, content }
    }
}

///Convert [file_content] to the image and normalise it
pub fn single_interpreter_input(
    file_content: &[u8],
    file_name: &str,
    shape: InterpreterInShape,
) -> Result<Array3<f32>, ComicPreprocessError> {
    debug!(
        "Proceed image file '{}'. Thread: {:?}",
        file_name,
        ::std::thread::current().name()
    );

    image::load_from_memory(&file_content)
        .map_err(|e| {
            error!("Can't proceed file as image. File: '{}'", file_name);
            e.into()
        })
        .map(|img| norm_image(img, shape))
}

///Batch all model inputs. Add empty items if needed to have same size as model input batch size
pub fn into_batched_interpreter_input(
    batched_items: Vec<InterpreterSingleInput>,
) -> InterpreterInput {
    let batch_size = batched_items.capacity();

    debug!(
        "Batch interpreter input. Count {}. Batch size {}",
        batched_items.len(),
        batch_size
    );

    let mut positions = Vec::with_capacity(batch_size);
    let mut names = Vec::with_capacity(batch_size);
    let mut content_vec = Vec::with_capacity(batch_size);

    for InterpreterSingleInput { pos, name, content } in batched_items {
        positions.push(pos);
        names.push(name);
        //Add one more axis. It will be used for stack later
        content_vec.push(content.insert_axis(Axis(0)))
    }

    //add empty items if needed
    if content_vec.len() < batch_size {
        for _ in 0..batch_size - content_vec.len() {
            let fake_array = ndarray::Array::from_elem(content_vec[0].dim(), 0.0f32);
            content_vec.push(fake_array);
        }
    }

    ///stack content arrays by added above axis
    fn stack_content(a: Vec<Array4<f32>>) -> Array4<f32> {
        let array_views: Vec<_> = a.iter().map(|a| a.view()).collect();

        ndarray::stack(Axis(0), &array_views).expect("Array should have same shape")
    }

    InterpreterInput {
        positions,
        names,
        content: stack_content(content_vec),
    }
}

///Normalise input image and convert it to the multidimensional float array
fn norm_image(img: image::DynamicImage, in_shape: InterpreterInShape) -> Array3<f32> {
    let (img_w, img_h) = in_shape.image_size();

    let img = img
        .resize_exact(img_w, img_h, image::FilterType::Nearest)
        .to_bgr();

    let img = Array3::from_shape_vec(
        in_shape.into_dimension().remove_axis(Axis(0)),
        img.into_raw().into_iter().map(|i| i as f32).collect(),
    )
    .expect("Image must be correct sized!");

    let img_data_size = img.len() as f32;

    let img_mean = img.sum() as f32 / img_data_size;

    let img_std = {
        let mut std_array = &img - img_mean;
        std_array.mapv_inplace(|i| i.powf(2.0f32));

        (std_array.sum() / img_data_size).sqrt()
    };

    (img - img_mean) / img_std
}
