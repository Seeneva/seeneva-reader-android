use super::{ObjectBox, ObjectDetection};
use crate::Config;

use std::collections::HashMap;

use ndarray::prelude::*;
use ndarray::{s, stack};

use itertools::{izip, Itertools};

///type for bbox transform operations
type BoxTransformTuple<D> = (Array<f32, D>, Array<f32, D>, Array<f32, D>, Array<f32, D>);

type BoxType<'a> = ArrayView<'a, f32, Ix1>;
//probability + box (cx, cy, w, h)
type BoxProb<'a> = (&'a f32, BoxType<'a>);

impl ObjectBox {
    ///Convert array of (cx, cy, w, h) to the structure
    /// Panic if length of the [a] is not equalt to 4
    fn from_array_view(a: ArrayView1<f32>) -> Self {
        if a.len() != 4 {
            panic!("Provided array can't be converted to PredictionBoundingBox");
        }

        ObjectBox {
            cx: a[0],
            cy: a[1],
            w: a[2],
            h: a[3],
        }
    }
}

///Parse response from TF interpreter [interpreter_output] and return result predictions
pub fn parse(interpreter_output: Array3<f32>, cfg: &Config) -> Vec<ObjectDetection> {
    let (det_class, det_probs, boxes) = slice_model_output(interpreter_output, cfg);

    //Iterate over each batch
    (0..det_class.shape()[0])
        .into_iter()
        .map(|batch_index| {
            let boxes = boxes.index_axis(Axis(0), batch_index);

            //Map of class_id to it list of boxes and probabilities
            let class_map: HashMap<u32, Vec<(&f32, ArrayView1<f32>)>> = {
                let det_probs = det_probs.index_axis(Axis(0), batch_index);
                let det_class = det_class.index_axis(Axis(0), batch_index);

                //iterator over tuple (&f32, &f32, ArrayView1<&f32>)
                izip!(det_class, det_probs, boxes.genrows())
                    //sort from biggest probs to lowest
                    .sorted_by(|(_, class_prob0, _), (_, class_prob1, _)| {
                        f32::partial_cmp(class_prob1, class_prob0).unwrap()
                    })
                    .into_iter()
                    //take only top N predictions
                    .take(cfg.topNDetection() as usize)
                    //group by class_id. class_id: (&prob, &box)
                    .fold(
                        HashMap::new(),
                        |mut acc: HashMap<u32, Vec<(&f32, ArrayView1<f32>)>>,
                         (class_id, class_prob, boxes)| {
                            acc.entry(*class_id)
                                .or_insert(vec![])
                                .push((class_prob, boxes));
                            acc
                        },
                    )
            };

            class_map
                .into_iter()
                .map(|(class_id, prob_boxes)| {
                    let keep_boxes = prob_boxes
                        .iter()
                        .take(prob_boxes.len() - 1)
                        .enumerate()
                        .map(|(i, c_box)| {
                            let mut extra_keep = vec![true; i + 1];
                            extra_keep.extend(
                                iou(&c_box.1, &prob_boxes[i + 1..])
                                    .mapv(|v| v <= cfg.nmsThresh())
                                    .into_iter(),
                            );

                            Array1::from_shape_vec(prob_boxes.len(), extra_keep).unwrap()
                        })
                        .fold(
                            Array1::from_shape_fn(prob_boxes.len(), |_| true),
                            |acc, v| acc & v,
                        );

                    let final_boxes = izip!(prob_boxes, keep_boxes.into_iter())
                        .filter_map(|((&box_prob, box_array), keep)| {
                            if *keep && box_prob > cfg.finalThreshold() {
                                Some((box_prob, ObjectBox::from_array_view(box_array)))
                            } else {
                                None
                            }
                        })
                        .collect_vec();

                    (class_id, final_boxes)
                })
                .fold(
                    HashMap::new(),
                    |mut acc: HashMap<u32, Vec<_>>, (class_id, boxes)| {
                        acc.insert(class_id, boxes);
                        acc
                    },
                )
                .into()
        })
        .collect_vec()
}

///Slice [interpreter_output] to the tuple (class, class_probability, boxes)
/// class and class_prob shape - (4, 9750) (batch_size, anchors_count)
/// boxes - (cx, cy, w, h). Shape (4, 9750, 4) (batch_size, anchors_count, stack of cx, cy, w, h)
fn slice_model_output(
    mut interpreter_output: Array3<f32>,
    cfg: &Config,
) -> (Array2<u32>, Array2<f32>, Array3<f32>) {
    let batch_size = interpreter_output.shape()[0];
    let class_count = cfg.classCount() as usize;

    //calculate non padded entries
    //slice and reshape network output
    let interpreter_output = {
        interpreter_output.slice_collapse(s![.., .., 0..cfg.total_output_count() as usize]);

        let (a_w, a_h) = {
            let size = cfg.anchorsSize();

            (size.w() as usize, size.h() as usize)
        };

        let res_shape = Ix4(
            batch_size,
            a_h,
            a_w,
            interpreter_output.len() / batch_size / a_w / a_h,
        );

        let interpreter_output = interpreter_output
            .to_owned()
            .into_shape(res_shape)
            .expect("Can't reshape interpreter output.");

        interpreter_output
    };

    let anchors_count = cfg.anchors_count();
    let anchors_per_grid = cfg.anchorPerGrid() as usize;

    //number of class probabilities, n classes for each anchor
    let num_class_probs = anchors_per_grid * class_count;

    //for 1 class it is always 1.0 in each position. Maybe should use anouther function, not softmax?
    let pred_class_probs = {
        //slice pred tensor to extract class pred scores and then normalize them
        let pred_class_probs = interpreter_output.slice(s![.., .., .., 0..num_class_probs]);

        let pred_class_probs_shape = Ix2(pred_class_probs.len() / class_count, class_count);

        let pred_class_probs = pred_class_probs
            .into_owned()
            .into_shape(pred_class_probs_shape)
            .expect("Can't reshape 'pred_class_probs' slice");

        let pred_class_probs_shape = Ix3(batch_size, anchors_count, class_count);

        softmax(pred_class_probs.view(), Axis(pred_class_probs.ndim() - 1))
            .into_shape(pred_class_probs_shape)
            .expect("Can't reshape 'pred_class_probs' softmax result")
    };

    //number of confidence scores, one for each anchor + class probs
    let num_confidence_scores = anchors_per_grid + num_class_probs;

    let pred_conf = {
        //slice the confidence scores and put them trough a sigmoid for probabilities
        let pred_conf = interpreter_output
            .slice(s![.., .., .., num_class_probs..num_confidence_scores])
            .into_owned()
            .into_shape(Ix2(batch_size, anchors_count))
            .expect("Can't reshape 'pred_conf'");

        sigmoid(pred_conf.view())
    };

    //compute class and probabilities
    let (det_class, det_probs) =
        det_class_width_probs(pred_class_probs, pred_conf, batch_size, anchors_count);

    //slice remaining bounding box_deltas
    let boxes = {
        //get bbox from predictions
        let bbox = {
            let pred_box_delta = interpreter_output
                .slice(s![.., .., .., num_confidence_scores..])
                .into_owned()
                .into_shape(Ix3(batch_size, anchors_count, 4))
                .expect("Can't reshape 'pred_box_delta'");

            //Converts prediction deltas to bounding boxes
            let delta_x = pred_box_delta.slice(s![.., .., 0]);
            let delta_y = pred_box_delta.slice(s![.., .., 1]);
            let delta_w = pred_box_delta.slice(s![.., .., 2]);
            let delta_h = pred_box_delta.slice(s![.., .., 3]);

            // get the coordinates and sizes of the anchor boxes from config
            let anchor_boxes =
                ArrayView2::from_shape(Ix2(anchors_count, 4), cfg.anchorBoxes().safe_slice())
                    .expect("Can't create ndarrray View from anchorBoxes slice");

            let anchor_x = anchor_boxes.slice(s![.., 0]);
            let anchor_y = anchor_boxes.slice(s![.., 1]);
            let anchor_w = anchor_boxes.slice(s![.., 2]);
            let anchor_h = anchor_boxes.slice(s![.., 3]);

            //as we only predict the deltas, we need to transform the anchor box values before computing the loss
            let exp_thresh = cfg.expThresh();

            let box_center_x = (&delta_x * &anchor_w) + anchor_x;
            let box_center_y = (&delta_y * &anchor_h) + anchor_y;

            let box_width = safe_exp(delta_w.view(), exp_thresh) * anchor_w;
            let box_height = safe_exp(delta_h.view(), exp_thresh) * anchor_h;

            (box_center_x, box_center_y, box_width, box_height)
        };

        //transform (cx, cy, w, h) to (xmin, ymin, xmax, ymax)
        let mut bbox = bbox_transform(bbox);

        {
            let (xmin, ymin, xmax, ymax) = &mut bbox;

            let box_fixer = |v: f32, min_v: f32, max_v: f32| -> f32 { v.max(min_v).min(max_v) };

            let (img_w, img_h) = {
                let img_size = cfg.image_size();

                ((img_size.w() - 1) as f32, (img_size.h() - 1) as f32)
            };

            xmin.mapv_inplace(|v| box_fixer(v, 0.0, img_w));
            ymin.mapv_inplace(|v| box_fixer(v, 0.0, img_h));
            xmax.mapv_inplace(|v| box_fixer(v, 0.0, img_w));
            ymax.mapv_inplace(|v| box_fixer(v, 0.0, img_h));
        }

        let (cx, cy, w, h) = bbox_transform_inv(bbox);

        let cx = cx.insert_axis(Axis(0));
        let cy = cy.insert_axis(Axis(0));
        let w = w.insert_axis(Axis(0));
        let h = h.insert_axis(Axis(0));

        //(batch_size, anchors_count, 4 - it is stack count)
        stack![Axis(0), cx, cy, w, h].permuted_axes([1, 2, 0])
    };

    (det_class, det_probs, boxes)
}

///Return tuple of (class_id, class_probability)
fn det_class_width_probs(
    pred_class_probs: Array3<f32>,
    pred_conf: Array2<f32>,
    batch_size: usize,
    anchors_count: usize,
) -> (Array2<u32>, Array2<f32>) {
    //[4, 9750, 1].
    // Last one is the number of class_id in the model
    let probs: Array<f32, Ix3> = pred_conf
        .into_shape(Ix3(batch_size, anchors_count, 1))
        .expect("Can't reshape 'pred_conf' during class and probs calculations")
        * pred_class_probs;

    //return both class and probs at same time

    //Iterate over all rows in the array it will return iterator over ArrayView<f32, IxN> N - number of classes
    //So we iterate over probabilities [class0_probability, class1_probability, ... , classN_probability]
    use ndarray::iter::{Iter, LanesIter};

    let probs_row_iter: LanesIter<f32, _> = probs.genrows().into_iter();

    let (det_class, det_probs): (Vec<u32>, Vec<f32>) = probs_row_iter
        .map(|v| {
            //so here is iterator over &f32
            let iter: Iter<f32, _> = v.into_iter();

            //If we have single class_id, we can skip whis step
            iter.enumerate()
                //After this step we determine which class detect each anchor in the model
                //One anchor = one detected class
                //Result is the class with biggest probability
                //
                //position - class_id, value - class_probability
                //Return (class_id, prob_of_the_class)
                .max_by(|(_, class_prob1), (_, class_prob2)| {
                    class_prob1.partial_cmp(class_prob2).unwrap()
                })
                .expect("The iterator over class probabilities is empty")
        })
        .fold((Vec::new(), Vec::new()), |(mut class, mut prob), (c, p)| {
            class.push(c as u32);
            prob.push(*p);

            (class, prob)
        });

    let shape = Ix2(probs.shape()[0], probs.shape()[1]);

    let det_probs =
        Array2::from_shape_vec(shape, det_probs).expect("Can't create 'det_probs' array");

    let det_class =
        Array2::from_shape_vec(shape, det_class).expect("Can't create 'det_class' array");

    (det_class, det_probs)
}

///Compute the Intersection-Over-Union of a batch of boxes with another box
/// Each box contains of (cx, cy, width, height)
fn iou(c_box: &BoxType, boxes: &[BoxProb]) -> Array1<f32> {
    let boxes = boxes
        .into_iter()
        .map(|(_, b)| b.insert_axis(Axis(0)))
        .collect_vec();

    let boxes = ndarray::stack(Axis(0), &boxes).unwrap();

    let boxes_cx = boxes.slice(s!(.., 0));
    let boxes_cy = boxes.slice(s!(.., 1));
    let boxes_w = boxes.slice(s!(.., 2));
    let boxes_h = boxes.slice(s!(.., 3));

    let box_w_half = c_box[2] * 0.5;
    let box_h_half = c_box[3] * 0.5;

    let boxes_w_half = &boxes_w * 0.5;
    let boxes_h_half = &boxes_h * 0.5;

    let intersection = {
        let intersection_w = ((&boxes_cx + &boxes_w_half)
            .mapv_into(|v| v.min(c_box[0] + box_w_half))
            - &(&boxes_cx - &boxes_w_half).mapv_into(|v| v.max(c_box[0] - box_w_half)))
            .mapv_into(|v| v.max(0.0));

        let intersection_h = ((&boxes_cy + &boxes_h_half)
            .mapv_into(|v| v.min(c_box[1] + box_h_half))
            - &(&boxes_cy - &boxes_h_half).mapv_into(|v| v.max(c_box[1] - box_h_half)))
            .mapv_into(|v| v.max(0.0));

        intersection_w * &intersection_h
    };

    let union = &boxes_w * &boxes_h + c_box[2] * c_box[3] - &intersection;

    intersection / union
}

fn softmax<D>(a: ArrayView<f32, D>, axis: Axis) -> Array<f32, D>
where
    D: Dimension + ndarray::RemoveAxis,
{
    let max = a
        .iter()
        .max_by(|x, y| {
            x.partial_cmp(y)
                .expect("Can't compare number in the softmax function")
        })
        .expect("Can't get max value from Array in the softmax function");

    let e_x = a.mapv(|v| f32::exp(v - max));

    let e_x_s = e_x.sum_axis(axis).insert_axis(axis);

    e_x / e_x_s
}

fn sigmoid<D>(a: ArrayView<f32, D>) -> Array<f32, D>
where
    D: Dimension,
{
    let mut e_x = a.mapv(|v| f32::exp(-v));
    e_x += 1.0f32;

    1.0 / e_x
}

///Safe exponential function
fn safe_exp<D>(a: ArrayView<f32, D>, exp_thresh: f32) -> Array<f32, D>
where
    D: Dimension,
{
    //if you need num-traits. Use S::from syntax e.g S::from(1.0 - exp_thresh) or Float::from(12.31)

    let slope = exp_thresh.exp();

    let lin_out = (&a - (exp_thresh + 1.0f32)) * slope;

    let lin_bool_f = |v: f32| -> bool { v > exp_thresh };

    let lin_region = a.mapv(|v| (lin_bool_f(v) as u8) as f32);

    let exp_out = a.mapv(|v| (if lin_bool_f(v) { 0.0f32 } else { v }).exp());

    //&lin_region*&lin_out + (&Array::ones(lin_region.shape()) - &lin_region) * &exp_out
    (1.0f32 - &lin_region) * exp_out + lin_region * lin_out
}
///convert a bbox of form (cx, cy, w, h) to (xmin, ymin, xmax, ymax)
fn bbox_transform(bbox: BoxTransformTuple<Ix2>) -> BoxTransformTuple<Ix2> {
    let (cx, cy, w, h) = bbox;

    let half_w = w / 2.0_f32;
    let half_h = h / 2.0_f32;

    let xmin = &cx - &half_w;
    let ymin = &cy - &half_h;
    let xmax = cx + half_w;
    let ymax = cy + half_h;

    (xmin, ymin, xmax, ymax)
}

///convert a bbox of form [xmin, ymin, xmax, ymax] to [cx, cy, w, h]
fn bbox_transform_inv(bbox: BoxTransformTuple<Ix2>) -> BoxTransformTuple<Ix2> {
    let (xmin, ymin, xmax, ymax) = bbox;

    let w = xmax - &xmin + 1.0f32;
    let h = ymax - &ymin + 1.0f32;

    let cx = &w * 0.5f32 + xmin;
    let cy = &h * 0.5f32 + ymin;

    (cx, cy, w, h)
}
