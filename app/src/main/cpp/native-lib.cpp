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
Eigen::Tensor<float, T> safe_exp_np(const Eigen::Tensor<float, T> &w, float expThresh) {
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

template<int R, typename T = Eigen::Tensor<float, R>>
void bbox_transform(T cx, T cy, T w, T h) {

}

//template<int R>
//void bbox_transform(Eigen::Tensor<float, R> cx, Eigen::Tensor<float, R> cy, Eigen::Tensor<float, R> w,
//                    Eigen::Tensor<float, R> h) {
//
//}

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

    auto anchorsTensorMap = Eigen::TensorMap<Eigen::Tensor<const float, 2>>(&comixConfig->anchorBoxes()->data()[0],
                                                                            comixConfig->anchorBoxes()->size() / 4, 4);

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
    Eigen::Tensor<float, 3> predClassProbs = (exp / exp.sum(along_class).eval().reshape(batch_by_one).broadcast(
            one_by_class)).reshape(ttt);




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


    //CONVERTS PREDICTION DELTAS TO BOUNDING BOXES

    Eigen::array<int, 3> predBoxDeltaOffsetX = {0, 0, 0};
    Eigen::array<int, 3> predBoxDeltaOffsetY = {0, 0, 1};
    Eigen::array<int, 3> predBoxDeltaOffsetW = {0, 0, 2};
    Eigen::array<int, 3> predBoxDeltaOffsetH = {0, 0, 3};

    Eigen::array<int, 3> predBoxDeltaExtent = {predBoxDelta.dimension(0), predBoxDelta.dimension(1), 1};

    auto boxDeltaX = predBoxDelta.slice(predBoxDeltaOffsetX, predBoxDeltaExtent);
    auto boxDeltaY = predBoxDelta.slice(predBoxDeltaOffsetY, predBoxDeltaExtent);
    auto boxDeltaW = predBoxDelta.slice(predBoxDeltaOffsetW, predBoxDeltaExtent);
    auto boxDeltaH = predBoxDelta.slice(predBoxDeltaOffsetH, predBoxDeltaExtent);

    //get the coordinates and sizes of the anchor boxes from config

//    Eigen::array<int, 2> anchorBoxOffsetX = {0, 0};
//    Eigen::array<int, 2> anchorBoxOffsetY = {0, 1};
//    Eigen::array<int, 2> anchorBoxOffsetW = {0, 2};
//    Eigen::array<int, 2> anchorBoxOffsetH = {0, 3};
//
//    Eigen::array<int, 2> anchorBoxExtent = {anchorsTensorMap.dimension(0), 1};

    auto r = anchorsTensorMap.reshape(
                    Eigen::array<long, 3>({1, anchorsTensorMap.dimension(0), anchorsTensorMap.dimension(1)}))
            .broadcast(Eigen::array<int, 3>({1, 1, 1}));

    auto anchorX = r.slice(predBoxDeltaOffsetX, predBoxDeltaExtent);
    auto anchorY = r.slice(predBoxDeltaOffsetY, predBoxDeltaExtent);
    auto anchorW = r.slice(predBoxDeltaOffsetW, predBoxDeltaExtent);
    auto anchorH = r.slice(predBoxDeltaOffsetH, predBoxDeltaExtent);
//
//    Eigen::Tensor<float, 2> sasd(anchorsTensorMap.dimension(0), anchorsTensorMap.dimension(1));
//    sasd.random();
//
    auto boxCenterX = boxDeltaX * anchorW + anchorX;
    auto boxCenterY = boxDeltaY * anchorH + anchorY;
    auto boxWidth = anchorW * safe_exp_np<3>(boxDeltaW, comixConfig->expThresh());
    auto boxHeight = anchorH * safe_exp_np<3>(boxDeltaH, comixConfig->expThresh());
    typedef Eigen::Tensor<float, 3> sssss;
    //std::tuple<sssss, sssss, sssss, sssss> asdasd;
    //asdasd = std::make_tuple<sssss, sssss, sssss, sssss>(boxCenterX.eval(), boxCenterY.eval(), boxWidth.eval(), boxHeight.eval());
    //bbox_transform<3>(std::make_tuple<sssss, sssss, sssss, sssss>(boxCenterX.eval(), boxCenterY.eval(), boxWidth.eval(), boxHeight.eval()));

    bbox_transform<3, int>(1, 2, 3, 4);

    //CONVERTS PREDICTION DELTAS TO BOUNDING BOXES

    Eigen::Tensor<float, 3> s = boxHeight;


    //Eigen::Tensor<float, 2> s(1, );
//        Eigen::array<int, 4> networkReshape{{batchSize,
//                                                    aHeigth,
//                                                    aWidth,
//                                                    (int) tPred->size() / batchSize / aHeigth / aWidth}};
    // e.reshape()

    __android_log_print(ANDROID_LOG_VERBOSE, "APPNAME", "TEST %f", s(0, 0, 0));

    AAsset_close(comixFlatbufferAsset);
}


