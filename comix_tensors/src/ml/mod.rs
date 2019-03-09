mod model_config;
mod prediction;

pub use self::model_config::*;
pub use self::prediction::*;

use ndarray::prelude::*;
use ndarray::IntoDimension;

/// Input shape of the Tensorflow interpreter. (batch_size, w, h, channels)
#[derive(Debug, Copy, Clone)]
pub struct InterpreterInShape(u32, u32, u32, u32);

impl InterpreterInShape {
    fn new(batch_size: u32, w: u32, h: u32, channels: u32) -> Self {
        InterpreterInShape(batch_size, w, h, channels)
    }

    pub fn batch_size(&self) -> u32 {
        self.0
    }

    /// (width, height)
    pub fn image_size(&self) -> (u32, u32) {
        (self.1, self.2)
    }

    pub fn channels(&self) -> u32 {
        self.3
    }
}

impl IntoDimension for InterpreterInShape {
    type Dim = Ix4;

    fn into_dimension(self) -> Self::Dim {
        // (batch_size, *h, w*, channels)
        Ix4(
            self.0 as usize,
            self.2 as usize,
            self.1 as usize,
            self.3 as usize,
        )
    }
}

/// (batch_size, anchors, anchor_per_grid)
#[derive(Debug, Copy, Clone)]
pub struct InterpreterOutShape(u32, usize, u32);

impl InterpreterOutShape {
    fn new(batch_size: u32, anchors_count: usize, anchors_per_grid: u32) -> Self {
        InterpreterOutShape(batch_size, anchors_count, anchors_per_grid)
    }
}

impl IntoDimension for InterpreterOutShape {
    type Dim = Ix3;

    fn into_dimension(self) -> Self::Dim {
        Ix3(self.0 as usize, self.1, self.2 as usize)
    }
}

///Describe batched input item of Tensorflow model
/// First Axis of [content] can be bigger than others. That mean that batch was not full
#[derive(Debug, Clone)]
pub struct InterpreterInput {
    pub positions: Vec<usize>,
    pub names: Vec<String>,
    pub content: Array4<f32>,
}

impl InterpreterInput {
    //    pub fn new(positions: Vec<usize>, names: Vec<String>, content: Array4<f32>)->Self{
    //
    //    }

    ///Return batch size of the model input
    pub fn batch_size(&self) -> usize {
        self.positions.len()
    }

    ///Return true if dimension of the [content] is the same as [batch_size]
    pub fn is_batch_full(&self) -> bool {
        self.content.dim().0 == self.batch_size()
    }
}

///Describe bathed output item of Tensorflow model
#[derive(Debug, Clone)]
pub struct InterpreterOutput {
    pub positions: Vec<usize>,
    pub names: Vec<String>,
    pub content: Array3<f32>,
}
