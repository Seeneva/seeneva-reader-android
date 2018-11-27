#[allow(non_snake_case)]
mod model_config_generated;

use self::model_config_generated::parser;
use self::parser::Size;
pub use self::parser::{get_root_as_config as parse_config, Config};

use super::{InterpreterInShape, InterpreterOutShape};

impl Config<'_> {
    pub fn anchors_pixel(&self) -> u32 {
        self.anchorsSize().square() * self.anchorPerGrid()
    }

    /// count of output per each anchor
    pub fn total_output_count(&self) -> u32 {
        // 1 - background, 4 - boxes (cx, cy, w, h)
        self.classCount() + 1 + 4
    }

    pub fn anchors_count(&self) -> usize {
        // 4 - because each box (cx cy w h)
        self.anchorBoxes().len() / 4
    }

    pub fn image_size(&self) -> &Size {
        self.image().size_()
    }

    ///Return input shape for TF interpreter
    pub fn interpreter_input_shape(&self) -> InterpreterInShape {
        let img = self.image();
        let img_size = img.size_();

        InterpreterInShape::new(img.batchSize(), img_size.w(), img_size.h(), img.channels())
    }

    ///Return output shape of TF interpreter
    pub fn interpreter_output_shape(&self) -> InterpreterOutShape {
        let img = self.image();

        InterpreterOutShape::new(img.batchSize(), self.anchors_count(), self.anchorPerGrid())
    }
}

impl Size {
    pub fn square(&self) -> u32 {
        self.h() * self.w()
    }
}
