#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <unsupported/Eigen/CXX11/Tensor>
#include <fstream>
#include "input.h"
#include "output.h"
#include "config_buf_generated.h"

using Eigen::Tensor;
template<int R> using fTensor = Tensor<float, R, Eigen::RowMajor>;
using f3Tensor = fTensor<3>;

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_almadevelop_comixreader_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */,
        jobject bitmap) {

    std::ifstream input_stream("testsd.txt");

    std::string line;
    while (getline(input_stream, line)) {
        // using printf() in all tests for consistency
        __android_log_print(ANDROID_LOG_VERBOSE, "APPNAME", "TEST %s", line.c_str());
    }

    const Tensor<float, 3> imageTensor = bitmapToTensor(env, bitmap);
    const Tensor<float, 3> imgNorm = preprocessImageTensor(imageTensor);
    return imageTensorToJavaArray(env, imgNorm);
}

namespace ComixReader {
    int getNumClassProbs(const Config *config) {
        return config->anchorPerGrid() * config->classCount();
    }

    /**
     *  number of confidence scores, one for each anchor + class probs
     */
    int getNumConfidenceScores(const Config *config) {
        return config->anchorPerGrid() + getNumClassProbs(config);
    }
}

template<int T>
fTensor<T> safeExp(const fTensor<T> &w, float expThresh) {
    const fTensor<T> thresh = w.constant(expThresh);
    const fTensor<T> slope = thresh.exp();

    const Eigen::Tensor<bool, T, Eigen::RowMajor> linBool = w > thresh;
    const fTensor<T> linRegion = linBool.template cast<float>();
    const fTensor<T> linOut = slope * (w - thresh + 1.0f);
    //exp_out = np.exp(np.where(lin_bool, np.zeros_like(w), w))
    const fTensor<T> exp_out = linBool.select(w.constant(0.0), w).exp();
    auto out = linRegion * linOut + (linRegion.constant(1.0) - linRegion) * exp_out;
    return out;
}

template<int R>
fTensor<R + 1> expandDim(const fTensor<R> &in) {
    Eigen::array<int, R + 1> reshapeParams;
    reshapeParams[0] = 1;

    for (size_t i = 0; i < in.dimensions().size(); i++) {
        reshapeParams[i + 1] = in.dimension(i);
    }

    return in.reshape(reshapeParams);
}

/**
 * Return stack of tensor inside array. Final shape will be (R, r0, r1, r2 ... R+1)
 * For example. If input (1, 9750, 1), then resul will be (4, 1, 9750, 1)
 */
template<int R>
fTensor<R + 1> stack(const std::vector<fTensor<R>> &tArray) {
    fTensor<R + 1> result;

    const size_t size = tArray.size();

    for (int i = 0; i < size; i++) {
        auto boxValue = tArray[i];

        Eigen::array<std::pair<ptrdiff_t, ptrdiff_t>, R + 1> paddings;

        // R r0 r1 r1 ... R
        for (int d = 0; d < R; d++) {
            if (d == 0) {
                paddings[0] = std::make_pair(i, size - 1 - i);
            } else {
                paddings[d] = std::make_pair(0, 0);
            }
        }

        //expand dimesion on first axis and add padding
        if (i == 0) {
            result = expandDim(boxValue).pad(paddings);
        } else {
            result += expandDim(boxValue).pad(paddings);
        }
    }
    return result;
}

/**
 * Return sorted indexes
 */
template<int R>
std::vector<size_t> argsort(const Eigen::TensorRef<fTensor<R>> &in) {
    // initialize original index locations
    std::vector<size_t> idx(in.size());

    std::iota(idx.begin(), idx.end(), 0);

    // sort indexes based on comparing values in v
    std::sort(idx.begin(), idx.end(),
              [&in](size_t i1, size_t i2) { return in(i1) < in(i2); });

    //auto test = Eigen::TensorMap<Eigen::Tensor<size_t, R>>(idx.data(), in.dimensions());

    return idx;
}

fTensor<2> softMax(const Eigen::TensorRef<fTensor<2>> &in) {
    const int batch_size = (int) in.dimension(0);
    const int num_classes = (int) in.dimension(1);

    const Eigen::DSizes<int, 1> along_class(1);
    const Eigen::DSizes<int, 2> batch_by_one(batch_size, 1);
    const Eigen::DSizes<int, 2> one_by_class(1, num_classes);

    const fTensor<0> max = in.maximum();

    auto exp = (in - in.constant(max(0))).exp();

    return (exp / exp.sum(along_class).eval()
            .reshape(batch_by_one)
            .broadcast(one_by_class));
}

fTensor<1> iouInterSide(const Eigen::TensorRef<fTensor<1>> &boxesCenters,
                        const Eigen::TensorRef<fTensor<1>> &boxesLen,
                        const float boxCenter,
                        const float boxLen) {
    auto boxesHalfLen = boxesLen * 0.5f;
    auto boxHalfLen = boxLen * 0.5f;

    //side of rectangle from cx to half of width
    auto boxesPlus = boxesCenters + boxesHalfLen;
    //side of rectangle from half of width to cx
    auto boxesMinus = boxesCenters - boxesHalfLen;

    //side of rectangle from cx to half of width
    auto boxPlus = boxesPlus.constant(boxCenter + boxHalfLen);
    //side of rectangle from half of width to cx
    auto boxMinus = boxesMinus.constant(boxCenter - boxHalfLen);

    auto plusCompare = boxesPlus < boxPlus;
    auto minusCompare = boxesMinus < boxMinus;

    auto side = plusCompare.select(boxesPlus, boxPlus) - minusCompare.select(boxMinus, boxesMinus);

    auto sideZero = side.constant(0.0f);

    auto sideCompareZero = side < sideZero;

    return sideCompareZero.select(sideZero, side);
}

/**
 * """
 * Compute the Intersection-Over-Union of a batch of boxes with another box.
 *
 * Args: box1: 2D array of[cx, cy, width, height]
 *
 * Returns:ious:array ofa float numberin range[0, 1].
 */
fTensor<1> batchIou(const Eigen::TensorRef<fTensor<2>> &boxes, const Eigen::TensorRef<fTensor<2>> &box) {
    const Eigen::array<int, 2> boxesCxOffset = {0, 0};
    const Eigen::array<int, 2> boxesCyOffset = {0, 1};
    const Eigen::array<int, 2> boxesWOffset = {0, 2};
    const Eigen::array<int, 2> boxesHOffset = {0, 3};
    const Eigen::array<int, 2> boxesExtent = {boxes.dimension(0), 1};

    const Eigen::array<int, 1> boxesShape = {boxes.dimension(0)};

    auto boxesCx = boxes.eval().slice(boxesCxOffset, boxesExtent).reshape(boxesShape);
    auto boxesCy = boxes.eval().slice(boxesCyOffset, boxesExtent).reshape(boxesShape);

    const fTensor<1> boxesW = boxes.eval().slice(boxesWOffset, boxesExtent).reshape(boxesShape);
    const fTensor<1> boxesH = boxes.eval().slice(boxesHOffset, boxesExtent).reshape(boxesShape);

    auto boxCx = box(0, 0);
    auto boxCy = box(0, 1);

    auto boxW = box(0, 2);
    auto boxH = box(0, 3);

    const fTensor<1> inter = iouInterSide(boxesCx, boxesW, boxCx, boxW) * iouInterSide(boxesCy, boxesH, boxCy, boxH);

    const fTensor<1> uni = boxesW * boxesH + boxW * boxH - inter;

//    for(int i = 0; i < boxes.size(); i++){
//        __android_log_print(ANDROID_LOG_VERBOSE, "AAA", "AA %f %f %f", inter(i), uni(i), inter(i) / uni(i));
//    }

//    fTensor<1> sd = inter / uni;
//
//    __android_log_print(ANDROID_LOG_VERBOSE, "AAA", "AA %f %f", inter(0), sd(0));

    return inter / uni;
}

/**
 * boxes: array of [cx, cy, w, h] (center format)
 * probs: array of probabilities
 * hreshold: two boxes are considered overlapping if their IOU is largher than this threshold form: 'center' or 'diagonal'
 *
 * Returns: keep: array of True or False.
 */
std::vector<bool> nonMaximumSupression(const fTensor<2> &boxes,
                                       const fTensor<1> &probs,
                                       const float threshold) {
    auto sortedProbsVec = argsort<1>(probs);
    std::reverse(sortedProbsVec.begin(), sortedProbsVec.end());

    std::vector<bool> keep(probs.size(), true);

    for (size_t i = 0; i < sortedProbsVec.size() - 1; i++) {
        const std::vector<size_t> boxesIds(sortedProbsVec.begin() + i + 1, sortedProbsVec.end());

        const Eigen::array<int, 2> boxesOffset = {(int) i + 1, 0};
        const Eigen::array<int, 2> boxesExtent = {boxes.dimension(0) - boxesOffset[0], boxes.dimension(1)};

        const fTensor<2> otherBoxes = boxes.slice(boxesOffset, boxesExtent);

        const Eigen::array<size_t, 2> boxOffset = {i, 0};
        const Eigen::array<int, 2> boxExtent = {1, boxes.dimension(1)};

        const fTensor<2> iBox = boxes.slice(boxOffset, boxExtent);

        const fTensor<1> iou = batchIou(otherBoxes, iBox);

        for (size_t j = 0; j < iou.size(); j++) {
            if (iou(j) > threshold) {
                keep[i + j + 1] = false;
            }
        }
    }

    return keep;
}

template<int R>
std::tuple<fTensor<R>, fTensor<R>, fTensor<R>, fTensor<R>>
bboxTransform(const fTensor<R> &cx, const fTensor<R> &cy, const fTensor<R> &w, const fTensor<R> &h) {
    auto halfW = w / w.constant(2.0);
    auto halfH = h / h.constant(2.0);

    auto xmin = cx - halfW;
    auto ymin = cy - halfH;
    auto xmax = cx + halfW;
    auto ymax = cy + halfH;

    return std::make_tuple(xmin, ymin, xmax, ymax);
}

/**
 * convert a bbox of form [xmin, ymin, xmax, ymax] to [cx, cy, w, h]
 */
template<int R>
std::vector<fTensor<R>>
bboxTransformInv(const fTensor<R> &xmin, const fTensor<R> &ymin, const fTensor<R> &xmax, const fTensor<R> &ymax) {
    const fTensor<R> w = xmax - xmin + 1.0f;
    const fTensor<R> h = ymax - ymin + 1.0f;

    const fTensor<R> cx = xmin + w * 0.5f;
    const fTensor<R> cy = ymin + h * 0.5f;

    return std::vector<fTensor<R>>{cx, cy, w, h};
}


template<int R>
fTensor<R> bboxMinMaxFilter(const fTensor<R> &t, float maxValue) {
    auto zeroTensor = t.constant(0.0);
    auto maxTensor = t.constant(maxValue);

    auto isLess = t < zeroTensor;
    auto isGreater = t > maxTensor;

    auto filtered = isGreater.select(maxTensor, isLess.select(zeroTensor, t));

    return filtered;
}

fTensor<3>
extractClassProbs(const fTensor<4> &predictions, const ComixReader::Config *config, int batchSize, int anchorsCount) {
    // slice pred tensor to extract class pred scores and then normalize them
    const Eigen::array<int, 4> classProbsOffsets = {0, 0, 0, 0};
    const Eigen::array<int, 4> classProbsExtents = {predictions.dimension(0),
                                                    predictions.dimension(1),
                                                    predictions.dimension(2),
                                                    ComixReader::getNumClassProbs(config)};

    auto classProbsAnchors = predictions.slice(classProbsOffsets, classProbsExtents).eval();
    auto classProbsAnchorsSize = ((Eigen::TensorRef<fTensor<4>>) classProbsAnchors).size();

    const Eigen::DSizes<int, 2> classProbsAnchorsShape((int) classProbsAnchorsSize / config->classCount(),
                                                       (int) config->classCount());

    auto softMaxed = softMax(classProbsAnchors.reshape(classProbsAnchorsShape));

    const Eigen::array<int, 3> classProbsShape = {batchSize, anchorsCount, (int) config->classCount()};

    return softMaxed.reshape(classProbsShape);
}

/**
 * slice the confidence scores and put them trough a sigmoid for probabilities
 */
fTensor<2> extractPredictionConfidence(const fTensor<4> &predictions,
                                       const ComixReader::Config *config,
                                       int batchSize,
                                       int anchorsCount) {
    const Eigen::array<int, 4> predConfOffsets = {0, 0, 0, ComixReader::getNumClassProbs(config)};

    Eigen::array<int, 4> predConfExtents;

    for (size_t i = 0; i < 3; i++) {
        predConfExtents[i] = (int) predictions.dimension(i);
    }

    predConfExtents[3] = std::min(ComixReader::getNumConfidenceScores(config),
                                  (int) predictions.dimension(3)) - predConfOffsets[3];


    const Eigen::array<int, 2> predConfShape = {batchSize, anchorsCount};

    return predictions.slice(predConfOffsets, predConfExtents).reshape(predConfShape).sigmoid();
}

f3Tensor extractBoxDeltas(const fTensor<4> &predictions,
                          const ComixReader::Config *config,
                          int batchSize,
                          int anchorsCount) {
    const Eigen::array<int, 4> predBoxOffsets = {0, 0, 0, ComixReader::getNumConfidenceScores(config)};

    Eigen::array<int, 4> predBoxExtents;

    for (size_t i = 0; i < 3; i++) {
        predBoxExtents[i] = (int) predictions.dimension(i);
    }

    predBoxExtents[3] = (int) predictions.dimension(3) - predBoxOffsets[3];

    //last - cx, cy, w, h
    const Eigen::DSizes<int, 3> zResize1(batchSize, anchorsCount, 4);

    return predictions.slice(predBoxOffsets, predBoxExtents).reshape(zResize1);
}


f3Tensor boxesFromDeltas(const fTensor<3> &predBoxDelta, const fTensor<2> &anchors, const ComixReader::Config *config) {
    const Eigen::array<int, 3> predBoxDeltaOffsetX = {0, 0, 0};
    const Eigen::array<int, 3> predBoxDeltaOffsetY = {0, 0, 1};
    const Eigen::array<int, 3> predBoxDeltaOffsetW = {0, 0, 2};
    const Eigen::array<int, 3> predBoxDeltaOffsetH = {0, 0, 3};

    const Eigen::array<int, 3> predBoxDeltaExtent = {predBoxDelta.dimension(0), predBoxDelta.dimension(1), 1};

    const Eigen::array<int, 2> predBoxDeltaShape = {predBoxDelta.dimension(0), predBoxDelta.dimension(1)};


    const fTensor<2> boxDeltaX = predBoxDelta.slice(predBoxDeltaOffsetX, predBoxDeltaExtent).reshape(predBoxDeltaShape);
    const fTensor<2> boxDeltaY = predBoxDelta.slice(predBoxDeltaOffsetY, predBoxDeltaExtent).reshape(predBoxDeltaShape);
    const fTensor<2> boxDeltaW = predBoxDelta.slice(predBoxDeltaOffsetW, predBoxDeltaExtent).reshape(predBoxDeltaShape);
    const fTensor<2> boxDeltaH = predBoxDelta.slice(predBoxDeltaOffsetH, predBoxDeltaExtent).reshape(predBoxDeltaShape);

    const Eigen::array<int, 2> anchorsOffsetX = {0, 0};
    const Eigen::array<int, 2> anchorsOffsetY = {0, 1};
    const Eigen::array<int, 2> anchorsOffsetW = {0, 2};
    const Eigen::array<int, 2> anchorsOffsetH = {0, 3};

    const Eigen::array<int, 2> anchorsExtent = {anchors.dimension(0), 1};

    const Eigen::array<int, 1> anchorsShape = {anchors.dimension(0)};

    const fTensor<1> anchorX = anchors.slice(anchorsOffsetX, anchorsExtent).reshape(anchorsShape);
    const fTensor<1> anchorY = anchors.slice(anchorsOffsetY, anchorsExtent).reshape(anchorsShape);
    const fTensor<1> anchorW = anchors.slice(anchorsOffsetW, anchorsExtent).reshape(anchorsShape);
    const fTensor<1> anchorH = anchors.slice(anchorsOffsetH, anchorsExtent).reshape(anchorsShape);

    const Eigen::array<int, 2> anchorIncreaseDim = {1, anchorW.dimensions()[0]};
    const Eigen::array<int, 2> anchorBroadcast = {boxDeltaX.dimensions()[0], 1};

    const fTensor<2> reshapedAnchorW = anchorW.reshape(anchorIncreaseDim).broadcast(anchorBroadcast);
    const fTensor<2> reshapedAnchorH = anchorH.reshape(anchorIncreaseDim).broadcast(anchorBroadcast);

    const fTensor<2> boxCenterX =
            anchorX.reshape(anchorIncreaseDim).broadcast(anchorBroadcast) + boxDeltaX * reshapedAnchorW;
    const fTensor<2> boxCenterY =
            anchorY.reshape(anchorIncreaseDim).broadcast(anchorBroadcast) + boxDeltaY * reshapedAnchorH;
    const fTensor<2> boxWidth = reshapedAnchorW * safeExp<2>(boxDeltaW, config->expThresh());
    const fTensor<2> boxHeight = reshapedAnchorH * safeExp<2>(boxDeltaH, config->expThresh());

    auto boxesTuple = bboxTransform<2>(boxCenterX, boxCenterY, boxWidth, boxHeight);
    auto xmin = bboxMinMaxFilter<2>(std::get<0>(boxesTuple), config->imageSize()->w() - 1.0);
    auto ymin = bboxMinMaxFilter<2>(std::get<1>(boxesTuple), config->imageSize()->h() - 1.0);
    auto xmax = bboxMinMaxFilter<2>(std::get<2>(boxesTuple), config->imageSize()->w() - 1.0);
    auto ymax = bboxMinMaxFilter<2>(std::get<3>(boxesTuple), config->imageSize()->h() - 1.0);

    auto test = bboxTransformInv<2>(xmin, ymin, xmax, ymax);

    for (int i = 0; i < 9000; i++) {
        float s = xmin(0, i);
        float s1 = xmax(0, i);

        fTensor<2> t = xmax - xmin + 1.0f;
        float tw = t(0, i);

        float bh = boxHeight(0, i);

        float s2 = test[0](0, i);
        float w = test[2](0, i);
        float h = test[3](0, i);
        float s4 = test[3](0, i);
    }


    return stack<2>(bboxTransformInv<2>(xmin, ymin, xmax, ymax)).shuffle((Eigen::array<int, 3>) {1, 2, 0});
}


using FilteredPredictions = std::tuple<std::vector<std::vector<float>>, std::vector<float>, std::vector<float>>;

/**
 * Filter bounding box predictions with probability threshold and non-maximum supression.
 *
 * boxes: array of [cx, cy, w, h]
 * probs: array of probabilities
 * clsIdx: array of class indices
 *
 * final_boxes: array of filtered bounding boxes.
 * final_probs: array of filtered probabilities
 * final_cls_idx: array of filtered class indices
 */
FilteredPredictions filterPrediction(const Eigen::TensorRef<fTensor<2>> &boxes,
                                     const Eigen::TensorRef<fTensor<1>> &probs,
                                     const Eigen::TensorRef<fTensor<1>> &clsIdx,
                                     const ComixReader::Config *config) {
    const size_t topDetection = config->topNDetection();

    std::vector<float> probsN(topDetection);
    std::vector<fTensor<1>> boxesN(topDetection);
    std::vector<float> clsIdxN(topDetection);


    if (config->topNDetection() > 0 && config->topNDetection() < probs.size()) {
        const auto idx = argsort(probs);
        const auto size = idx.size();

        for (size_t i = 0; i < config->topNDetection(); i++) {
            const size_t id = idx[size - 1 - i];
            probsN[i] = probs(id);
            clsIdxN[i] = clsIdx(id);

            const Eigen::array<int, 2> boxSliceOffset = {(int) id, 0};
            const Eigen::array<int, 2> boxSliceExtent = {1, boxes.dimension(1)};

            boxesN[i] = boxes.eval()
                    .slice(boxSliceOffset, boxSliceExtent)
                    .reshape(Eigen::array<int, 1>{boxes.dimension(1)});

            float x = boxesN[i](0);
            float y = boxesN[i](1);
            float w = boxesN[i](2);
            float h = boxesN[i](3);

            float hd = boxesN[i](3);
        }

    } else {
        //TODO
    }

    std::vector<std::vector<float>> boxesFinal;
    std::vector<float> probsFinal;
    std::vector<float> clsIdxFinal;

    for (uint32_t classId = 0; classId < config->classCount(); classId++) {
        //prob positions with specific classId
        std::vector<uint32_t> idxPerClass;

        std::vector<fTensor<1>> boxesPerClass;
        std::vector<float> probsPerClass;

        for (uint32_t probId = 0; probId < probsN.size(); probId++) {
            if (clsIdxN[probId] == classId) {
                idxPerClass.push_back(probId);

                boxesPerClass.push_back(boxesN[probId]);
                probsPerClass.push_back(probsN[probId]);
            }
        }

        const fTensor<1> probsMap = Eigen::TensorMap<fTensor<1>>(probsPerClass.data(), probsPerClass.size());
        const fTensor<2> boxesStack = stack<1>(boxesPerClass);

        auto keep = nonMaximumSupression(boxesStack, probsMap, config->nmsThresh());

        for (size_t i = 0; i < keep.size(); i++) {
            if (keep[i]) {
                auto id = idxPerClass[i];
                const float *boxData = boxesN[id].data();

                boxesFinal.push_back(std::vector<float>(boxData, boxData + boxesN[id].size()));
                probsFinal.push_back(probsN[id]);
                clsIdxFinal.push_back(clsIdxN[id]);
            }
        }
    }

    return std::make_tuple(boxesFinal, probsFinal, clsIdxFinal);
}


extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_almadevelop_comixreader_MainActivity_parsePrediction(
        JNIEnv *env,
        jobject /* this */,
        jobject assetManager,
        jobjectArray pred,
        jint batchSize) {
    //PARSE FLATBUFFER CONFIG
    AAssetManager *mng = AAssetManager_fromJava(env, assetManager);
    AAsset *comixFlatbufferAsset = AAssetManager_open(mng, "comix.dat", AASSET_MODE_BUFFER);
    char *configDataBuffer = (char *) AAsset_getBuffer(comixFlatbufferAsset);
    const ComixReader::Config *comixConfig = ComixReader::GetConfig(configDataBuffer);

    //_________________________

    auto anchorBoxesRowMajorMap = Eigen::TensorMap<Eigen::Tensor<const float, 2, Eigen::RowMajor>>(
            comixConfig->anchorBoxes()->data(), comixConfig->anchorBoxes()->size() / 4, 4);

//    Eigen::Tensor<float, 2> anchorsTensorMap = anchorBoxesRowMajorMap.swap_layout().shuffle(
//            (Eigen::array<int, 2>) {1, 0});

    fTensor<2> anchorsTensorMap = anchorBoxesRowMajorMap;

    const int anchorsCount = (int) anchorsTensorMap.dimension(0);

    PredictionInfo predInfo;
    predInfo.classCount = comixConfig->classCount();
    predInfo.batchSize = batchSize;
    predInfo.anchors = (int) anchorsTensorMap.dimension(0);
    predInfo.aHeigth = comixConfig->anchorsSize()->h();
    predInfo.aWidth = comixConfig->anchorsSize()->w();
    predInfo.aPerGrid = comixConfig->anchorPerGrid();

    const fTensor<4> tPred = parsePredictions(env, pred, predInfo);


    //predClassProbs if 1 class - always contains 1.0
    const f3Tensor predClassProbs = extractClassProbs(tPred, comixConfig, batchSize, anchorsCount);
    const fTensor<2> predConf = extractPredictionConfidence(tPred, comixConfig, batchSize, anchorsCount);
    const f3Tensor predBoxDelta = extractBoxDeltas(tPred, comixConfig, batchSize, anchorsCount);

    const f3Tensor boxes = boxesFromDeltas(predBoxDelta, anchorsTensorMap, comixConfig);


    const f3Tensor probs =
            predClassProbs * predConf.reshape(Eigen::array<int, 3>{batchSize, anchorsCount, 1});
    const fTensor<2> detProbs = probs.maximum(Eigen::array<int, 1>({2}));
    const fTensor<2> detClass = probs.argmax(2).cast<float>();


    //Final shape for detProbs and detClass slice
    const Eigen::array<int, 1> detShape = {anchorsCount};
    const Eigen::array<int, 2> boxesShape = {anchorsCount, 4};

    std::vector<std::vector<float>> allBoxes;
    std::vector<float> allScores;
    std::vector<float> allClassId;

    for (int b = 0; b < batchSize; b++) {
        const Eigen::array<int, 2> detBatchSliceOffset = {b, 0};
        const Eigen::array<int, 2> detBatchSliceExtents = {1, detShape[0]};

        const Eigen::array<int, 3> boxBatchSliceOffset = {b, 0, 0};
        const Eigen::array<int, 3> boxBatchSliceExtents = {1, boxesShape[0], boxesShape[1]};

        const auto fiteredPredictions = filterPrediction(
                (fTensor<2>) boxes.slice(boxBatchSliceOffset, boxBatchSliceExtents).reshape(boxesShape),
                detProbs.slice(detBatchSliceOffset, detBatchSliceExtents).reshape(detShape).eval(),
                detClass.slice(detBatchSliceOffset, detBatchSliceExtents).reshape(detShape).eval(),
                comixConfig);

        auto filteredBoxes = std::get<0>(fiteredPredictions);
        auto filteredScores = std::get<1>(fiteredPredictions);
        auto filteredClassIdx = std::get<2>(fiteredPredictions);

        //filter by finalThreshold
        for (auto it = filteredScores.begin(); it != filteredScores.end();) {
            const auto score = *it;

            if (score < comixConfig->finalThreshold()) {
                it = filteredScores.erase(it);

                auto pos = it - filteredScores.begin();
                filteredBoxes.erase(filteredBoxes.begin() + pos);
                filteredClassIdx.erase(filteredClassIdx.begin() + pos);
            } else {
                ++it;
            }
        }

        allBoxes.assign(filteredBoxes.begin(), filteredBoxes.end());
        allScores.assign(filteredScores.begin(), filteredScores.end());
        allClassId.assign(filteredClassIdx.begin(), filteredClassIdx.end());

        __android_log_print(ANDROID_LOG_VERBOSE, "A", "B");
    }

    AAsset_close(comixFlatbufferAsset);

    const jclass floatArrayCls = env->FindClass("[F");

    jobjectArray array = env->NewObjectArray((jsize) allBoxes.size(), floatArrayCls, NULL);

    for (auto it = allBoxes.begin(); it != allBoxes.end(); ++it) {
        auto pos = it - allBoxes.begin();

        auto boxArray = env->NewFloatArray((jsize) 4);
        env->SetFloatArrayRegion(boxArray, 0, 4, (*it).data());

        env->SetObjectArrayElement(array, pos, boxArray);
        env->DeleteLocalRef(boxArray);
    }

    return array;
}


