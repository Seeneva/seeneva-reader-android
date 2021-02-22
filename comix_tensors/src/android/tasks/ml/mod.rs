use super::{spawn_task, SpawnTaskResult};
pub use {self::tesseract::*, self::tflite::*};

mod tesseract;
mod tflite;
