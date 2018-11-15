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
auto bboxTransformInv(const fTensor<R> &xmin, const fTensor<R> &ymin, const fTensor<R> &xmax, const fTensor<R> &ymax) {
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

template<int R, size_t A>
fTensor<R + 1> stack(const std::array<fTensor<R>, A> &tArray) {
    fTensor<R + 1> result;

    for (int i = 0; i < tArray.size(); i++) {
        auto boxValue = tArray[i];

        Eigen::array<std::pair<ptrdiff_t, ptrdiff_t>, R + 1> paddings;

        Eigen::array<int, R + 1> reshapeParams;
        reshapeParams[0] = 1;

        for (int d = 0; d < R; d++) {
            reshapeParams[d + 1] = boxValue.dimension(d);

            if (d == 0) {
                paddings[0] = std::make_pair(i, R - i);
            } else {
                paddings[d] = std::make_pair(0, 0);
            }
        }

        if (i == 0) {
            result = boxValue.reshape(reshapeParams).pad(paddings);
        } else {
            result += boxValue.reshape(reshapeParams).pad(paddings);
        }
    }
    return result;
}

void boxesFromDeltas(const fTensor<3> &predBoxDelta, const fTensor<2> &anchors, const ComixReader::Config *config) {
    Eigen::array<int, 3> predBoxDeltaOffsetX = {0, 0, 0};
    Eigen::array<int, 3> predBoxDeltaOffsetY = {0, 0, 1};
    Eigen::array<int, 3> predBoxDeltaOffsetW = {0, 0, 2};
    Eigen::array<int, 3> predBoxDeltaOffsetH = {0, 0, 3};

    Eigen::array<int, 3> predBoxDeltaExtent = {predBoxDelta.dimension(0), predBoxDelta.dimension(1), 1};

    auto boxDeltaX = predBoxDelta.slice(predBoxDeltaOffsetX, predBoxDeltaExtent);
    auto boxDeltaY = predBoxDelta.slice(predBoxDeltaOffsetY, predBoxDeltaExtent);
    auto boxDeltaW = predBoxDelta.slice(predBoxDeltaOffsetW, predBoxDeltaExtent);
    auto boxDeltaH = predBoxDelta.slice(predBoxDeltaOffsetH, predBoxDeltaExtent);

    auto r = anchors.reshape(
                    Eigen::array<long, 3>({1, anchors.dimension(0), anchors.dimension(1)}))
            .broadcast(Eigen::array<int, 3>({1, 1, 1}));

    auto anchorX = r.slice(predBoxDeltaOffsetX, predBoxDeltaExtent);
    auto anchorY = r.slice(predBoxDeltaOffsetY, predBoxDeltaExtent);
    auto anchorW = r.slice(predBoxDeltaOffsetW, predBoxDeltaExtent);
    auto anchorH = r.slice(predBoxDeltaOffsetH, predBoxDeltaExtent);


    auto boxCenterX = boxDeltaX * anchorW + anchorX;
    auto boxCenterY = boxDeltaY * anchorH + anchorY;
    auto boxWidth = anchorW * safeExp<3>(boxDeltaW, config->expThresh());
    auto boxHeight = anchorH * safeExp<3>(boxDeltaH, config->expThresh());

    auto boxesTuple = bboxTransform<3>(boxCenterX, boxCenterY, boxWidth, boxHeight);
    auto xmin = bboxMinMaxFilter<3>(std::get<0>(boxesTuple), config->imageSize()->w() - 1.0);
    auto ymin = bboxMinMaxFilter<3>(std::get<1>(boxesTuple), config->imageSize()->h() - 1.0);
    auto xmax = bboxMinMaxFilter<3>(std::get<2>(boxesTuple), config->imageSize()->w() - 1.0);
    auto ymax = bboxMinMaxFilter<3>(std::get<3>(boxesTuple), config->imageSize()->h() - 1.0);

    auto boxesFromDeltas = stack(bboxTransformInv<3>(xmin, ymin, xmax, ymax)).shuffle(
            (Eigen::array<int, 4>) {1, 2, 0, 3});

    fTensor<4> sadasd = boxesFromDeltas;

    __android_log_print(ANDROID_LOG_VERBOSE, "APPNAME", "TEST %f", sadasd(50, 0, 0, 0));
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


    PredictionInfo predInfo;
    predInfo.classCount = comixConfig->classCount();
    predInfo.batchSize = batchSize;
    predInfo.anchors = (int) anchorsTensorMap.dimension(0);
    predInfo.aHeigth = comixConfig->anchorsSize()->h();
    predInfo.aWidth = comixConfig->anchorsSize()->w();
    predInfo.aPerGrid = comixConfig->anchorPerGrid();

    const int totalOutputCount = getTotalOutputCount(predInfo.classCount);
    //number of class probabilities, n classes for each anchor
    const int numClassProbs = predInfo.aPerGrid * predInfo.classCount;

    const Tensor<float, 4> tPred = parsePredictions(env, pred, predInfo);

    // slice pred tensor to extract class pred scores and then normalize them
    Eigen::array<int, 4> tOffsets = {0, 0, 0, 0};
    Eigen::array<int, 4> tExtents = {tPred.dimension(0), tPred.dimension(1), tPred.dimension(2), numClassProbs};


    Eigen::Tensor<float, 4> e = tPred.slice(tOffsets, tExtents);
    array<int, 2> networkReshape{{e.size() / predInfo.classCount, predInfo.classCount}};
    Eigen::Tensor<float, 2> resE = e.reshape(networkReshape);

    const int kBatchDim = 0;
    const int kClassDim = 1;

    const int batch_size = (int) resE.dimension(kBatchDim);
    const int num_classes = (int) resE.dimension(kClassDim);

    Eigen::DSizes<int, 1> along_class(1);
    Eigen::DSizes<int, 2> batch_by_one(batch_size, 1);
    Eigen::DSizes<int, 2> one_by_class(1, num_classes);

    Eigen::Tensor<float, 0> max = resE.maximum();
    Eigen::Tensor<float, 2> exp = (resE - resE.constant(max(0))).exp();
    Eigen::DSizes<int, 3> ttt(predInfo.batchSize, predInfo.anchors, predInfo.classCount);
    Eigen::Tensor<float, 3> predClassProbs = (exp / exp.sum(along_class).eval()
            .reshape(batch_by_one)
            .broadcast(one_by_class))
            .reshape(ttt);




    // number of confidence scores, one for each anchor + class probs
    const int num_confidence_scores = predInfo.aPerGrid + numClassProbs;
    // slice the confidence scores and put them trough a sigmoid for probabilities
    Eigen::array<int, 4> yOffsets = {0, 0, 0, numClassProbs};
    Eigen::array<int, 4> yExtents = {tPred.dimension(0), tPred.dimension(1), tPred.dimension(2),
                                     std::min(num_confidence_scores, (int) tPred.dimension(3)) - yOffsets[3]};
    Eigen::DSizes<int, 2> yResize1(predInfo.batchSize, predInfo.anchors);

    Eigen::Tensor<float, 2> predConf = tPred.slice(yOffsets, yExtents).reshape(yResize1).sigmoid();


    Eigen::array<int, 4> zOffsets = {0, 0, 0, num_confidence_scores};
    Eigen::array<int, 4> zExtents = {tPred.dimension(0), tPred.dimension(1), tPred.dimension(2),
                                     (int) tPred.dimension(3) - zOffsets[3]};

    Eigen::DSizes<int, 3> zResize1(predInfo.batchSize, predInfo.anchors, 4);

    Eigen::Tensor<float, 3> predBoxDelta = tPred.slice(zOffsets, zExtents).reshape(zResize1);


    boxesFromDeltas(predBoxDelta, anchorsTensorMap, comixConfig);

    AAsset_close(comixFlatbufferAsset);
}


