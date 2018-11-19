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
template<int R> using fTensor = Tensor<float, R>;
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
Eigen::Tensor<float, T> safeExp(const Eigen::Tensor<float, T> &w, float expThresh) {
    auto thresh = w.constant(expThresh);
    auto slope = thresh.exp();

    auto linBool = w > thresh;
    auto linRegion = linBool.template cast<float>();
    auto linOut = slope * (w - thresh + w.constant(1.0));
    //exp_out = np.exp(np.where(lin_bool, np.zeros_like(w), w))
    auto exp_out = linBool.select(w.constant(0.0), w);
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
template<int R, size_t A>
fTensor<R + 1> stack(const std::array<fTensor<R>, A> &tArray) {
    fTensor<R + 1> result;

    for (int i = 0; i < tArray.size(); i++) {
        auto boxValue = tArray[i];

        Eigen::array<std::pair<ptrdiff_t, ptrdiff_t>, R + 1> paddings;

        // R r0 r1 r1 ... R
        for (int d = 0; d < R; d++) {
            if (d == 0) {
                paddings[0] = std::make_pair(i, R - i);
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
Eigen::Tensor<size_t, R> argsort(const Eigen::TensorRef<fTensor<R>> &in) {
    // initialize original index locations
    std::vector<size_t> idx(in.size());

    std::iota(idx.begin(), idx.end(), 0);

    // sort indexes based on comparing values in v
    std::sort(idx.begin(), idx.end(),
              [&in](size_t i1, size_t i2) { return in(i1) < in(i2); });

    Eigen::array<int, R> sas;

    auto test = Eigen::TensorMap<Eigen::Tensor<size_t, R>>(idx.data(), in.dimensions());

    size_t t = test(1);

    return test;
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
std::array<fTensor<R>, 4>
bboxTransformInv(const fTensor<R> &xmin, const fTensor<R> &ymin, const fTensor<R> &xmax, const fTensor<R> &ymax) {
    auto w = xmax - xmin + xmax.constant(1.0);
    auto h = ymax - ymin + ymax.constant(1.0);

    auto cx = xmin + w * w.constant(0.5);
    auto cy = ymin + h * h.constant(0.5);

    return std::array<fTensor<R>, 4>{cx, cy, w, h};
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


fTensor<4>
boxesFromDeltas(const fTensor<3> &predBoxDelta, const fTensor<2> &anchors, const ComixReader::Config *config) {
    Eigen::array<int, 3> predBoxDeltaOffsetX = {0, 0, 0};
    Eigen::array<int, 3> predBoxDeltaOffsetY = {0, 0, 1};
    Eigen::array<int, 3> predBoxDeltaOffsetW = {0, 0, 2};
    Eigen::array<int, 3> predBoxDeltaOffsetH = {0, 0, 3};

    Eigen::array<int, 3> predBoxDeltaExtent = {predBoxDelta.dimension(0), predBoxDelta.dimension(1), 1};

    auto boxDeltaX = predBoxDelta.slice(predBoxDeltaOffsetX, predBoxDeltaExtent);
    auto boxDeltaY = predBoxDelta.slice(predBoxDeltaOffsetY, predBoxDeltaExtent);
    auto boxDeltaW = predBoxDelta.slice(predBoxDeltaOffsetW, predBoxDeltaExtent);
    auto boxDeltaH = predBoxDelta.slice(predBoxDeltaOffsetH, predBoxDeltaExtent);

    //increase anchors count to be the same as predBoxDelta
    auto expandedAnchors = expandDim(anchors);

    auto anchorX = expandedAnchors.slice(predBoxDeltaOffsetX, predBoxDeltaExtent);
    auto anchorY = expandedAnchors.slice(predBoxDeltaOffsetY, predBoxDeltaExtent);
    auto anchorW = expandedAnchors.slice(predBoxDeltaOffsetW, predBoxDeltaExtent);
    auto anchorH = expandedAnchors.slice(predBoxDeltaOffsetH, predBoxDeltaExtent);


    auto boxCenterX = boxDeltaX * anchorW + anchorX;
    auto boxCenterY = boxDeltaY * anchorH + anchorY;
    auto boxWidth = anchorW * safeExp<3>(boxDeltaW, config->expThresh());
    auto boxHeight = anchorH * safeExp<3>(boxDeltaH, config->expThresh());

    auto boxesTuple = bboxTransform<3>(boxCenterX, boxCenterY, boxWidth, boxHeight);
    auto xmin = bboxMinMaxFilter<3>(std::get<0>(boxesTuple), config->imageSize()->w() - 1.0);
    auto ymin = bboxMinMaxFilter<3>(std::get<1>(boxesTuple), config->imageSize()->h() - 1.0);
    auto xmax = bboxMinMaxFilter<3>(std::get<2>(boxesTuple), config->imageSize()->w() - 1.0);
    auto ymax = bboxMinMaxFilter<3>(std::get<3>(boxesTuple), config->imageSize()->h() - 1.0);

    return stack<3>(bboxTransformInv<3>(xmin, ymin, xmax, ymax)).shuffle((Eigen::array<int, 4>) {1, 2, 0, 3});
}

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
void filterPrediction(const Eigen::TensorRef<f3Tensor> &boxes,
                      const Eigen::TensorRef<fTensor<1>> &probs,
                      const Eigen::TensorRef<fTensor<1>> &clsIdx,
                      const ComixReader::Config *config) {
    const size_t topDetection = config->topNDetection();

    std::vector<float> probsN(topDetection);
    std::vector<fTensor<2>> boxesN(topDetection);
    std::vector<float> clsIdxN(topDetection);

    if (config->topNDetection() > 0 && config->topNDetection() < probs.size()) {
        const auto idx = argsort(probs);
        const auto size = idx.size();

        for (size_t i = 0; i < config->topNDetection(); i++) {
            const size_t id = idx(size - 1 - i);
            probsN[i] = probs(id);
            clsIdxN[i] = clsIdx(id);

            const fTensor<2> s = boxes.eval().slice(Eigen::array<int, 3>{(int) id, 0, 0},
                                                    Eigen::array<int, 3>{1, boxes.dimension(1), boxes.dimension(2)})
                    .reshape(Eigen::array<int, 2>{boxes.dimension(1), boxes.dimension(2)});

            __android_log_print(ANDROID_LOG_VERBOSE, "APP", "test %i", id);
        }

        //__android_log_print(ANDROID_LOG_VERBOSE, "APP", "test %i", s(0));
    } else {
        //TODO
    }
}


extern "C" JNIEXPORT void JNICALL
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

    Eigen::Tensor<float, 2> anchorsTensorMap = anchorBoxesRowMajorMap.swap_layout().shuffle(
            (Eigen::array<int, 2>) {1, 0});

    const int anchorsCount = (int) anchorsTensorMap.dimension(0);


    PredictionInfo predInfo;
    predInfo.classCount = comixConfig->classCount();
    predInfo.batchSize = batchSize;
    predInfo.anchors = (int) anchorsTensorMap.dimension(0);
    predInfo.aHeigth = comixConfig->anchorsSize()->h();
    predInfo.aWidth = comixConfig->anchorsSize()->w();
    predInfo.aPerGrid = comixConfig->anchorPerGrid();

    const Tensor<float, 4> tPred = parsePredictions(env, pred, predInfo);

    f3Tensor predClassProbs = extractClassProbs(tPred, comixConfig, batchSize, anchorsCount);
    fTensor<2> predConf = extractPredictionConfidence(tPred, comixConfig, batchSize, anchorsCount);
    f3Tensor predBoxDelta = extractBoxDeltas(tPred, comixConfig, batchSize, anchorsCount);

    const auto boxes = boxesFromDeltas(predBoxDelta, anchorsTensorMap, comixConfig);

    Eigen::Tensor<float, 3> probs = predClassProbs * predConf.reshape(Eigen::array<int, 3>{batchSize, anchorsCount, 1});
    auto detProbs = probs.maximum(Eigen::array<int, 1>({2}));
    auto detClass = probs.argmax(2).cast<float>();

    //Final shape for detProbs and detClass slice
    const Eigen::array<int, 1> detShape = {anchorsCount};
    const Eigen::array<int, 3> boxesShape = {anchorsCount, 4, boxes.dimension(3)};

    for (int b = 0; b < batchSize; b++) {
        const Eigen::array<int, 2> detBatchSliceOffset = {b, 0};
        const Eigen::array<int, 2> detBatchSliceExtents = {1, detShape[0]};

        const Eigen::array<int, 4> boxBatchSliceOffset = {b, 0, 0, 0};
        const Eigen::array<int, 4> boxBatchSliceExtents = {1, boxesShape[0], boxesShape[1], boxesShape[2]};

        filterPrediction(boxes.slice(boxBatchSliceOffset, boxBatchSliceExtents).reshape(boxesShape).eval(),
                         detProbs.slice(detBatchSliceOffset, detBatchSliceExtents).reshape(detShape).eval(),
                         detProbs.slice(detBatchSliceOffset, detBatchSliceExtents).reshape(detShape).eval(),
                         comixConfig);

        //__android_log_print(ANDROID_LOG_VERBOSE, "APP", "test %f", t(0));

        //fTensor<0> asd = det_class.chip(0, 0);
    }

    //__android_log_print(ANDROID_LOG_VERBOSE, "APP", "test %f", asd(0, 0));

    AAsset_close(comixFlatbufferAsset);
}


