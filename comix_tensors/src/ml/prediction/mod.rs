mod parser;

use super::{Config, InterpreterOutput};

use std::collections::HashMap;

use itertools::Itertools;

type ClassId = u32;
type Probability = f32;
///Vector of all predictions by class
type PredictionsByClass = Vec<(Probability, ObjectBox)>;

///Describe comic page file prediction
#[derive(Debug, Clone)]
pub struct ComicPageObjects {
    ///position of the file in the comic container
    pub page_position: usize,
    ///file name
    pub page_name: String,
    ///All file's decoded predictions
    pub objects: ObjectDetection,
}

impl ComicPageObjects {
    fn new(page_position: usize, page_name: String, objects: ObjectDetection) -> Self {
        ComicPageObjects {
            page_position,
            page_name,
            objects,
        }
    }
}

///Contain predicted Bbox for each ClassId
#[derive(Debug, Clone)]
pub struct ObjectDetection(HashMap<ClassId, PredictionsByClass>);

impl From<HashMap<ClassId, PredictionsByClass>> for ObjectDetection {
    fn from(m: HashMap<ClassId, PredictionsByClass>) -> Self {
        ObjectDetection(m)
    }
}

impl ObjectDetection {
    pub fn into_iter(self) -> impl Iterator<Item = (ClassId, PredictionsByClass)> {
        self.0.into_iter()
    }
}

///Describe bounding box of the predicted class
#[derive(Debug, Copy, Clone)]
pub struct ObjectBox {
    ///x coordinate of center
    pub cx: f32,
    ///y coordinate of center
    pub cy: f32,
    ///width of speech baloon
    pub w: f32,
    ///height of speech baloon
    pub h: f32,
}

///Parse TF interpreter output [interpeter_output]
/// Return batched comic page objects which contain all supported objects at each page
pub fn get_comic_pages_objects(
    interpeter_output: InterpreterOutput,
    config: &Config,
) -> Vec<ComicPageObjects> {
    let InterpreterOutput {
        positions,
        names,
        content,
    } = interpeter_output;

    izip!(positions, names, parser::parse(content, config))
        .map(|(pos, name, prediction)| ComicPageObjects::new(pos, name, prediction))
        .collect_vec()
}
