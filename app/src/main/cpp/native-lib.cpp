#include <jni.h>
#include <string>
#include <android/log.h>
#include <unsupported/Eigen/CXX11/Tensor>
#include <fstream>
#include "input.h"
#include "output.h"

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

extern "C" JNIEXPORT void JNICALL
Java_com_almadevelop_comixreader_MainActivity_parsePrediction(
        JNIEnv *env,
        jobject /* this */,
        jobjectArray pred,
        jint cCount,
        jint batchSize,
        jint anchors,
        jint aHeigth,
        jint aWidth,
        jint aPerGrid) {

    PredictionInfo predInfo;
    predInfo.classCount = cCount;
    predInfo.batchSize = batchSize;
    predInfo.anchors = anchors;
    predInfo.aHeigth = aHeigth;
    predInfo.aWidth = aWidth;
    predInfo.aPerGrid = aPerGrid;

    const int totalOutputCount = getTotalOutputCount(cCount);
    //number of class probabilities, n classes for each anchor
    const int numClassProbs = aPerGrid * cCount;

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


    Eigen::array<int, 3> predBoxDeltaOffsetX = {0, 0, 0};
    Eigen::array<int, 3> predBoxDeltaOffsetY = {0, 0, 1};
    Eigen::array<int, 3> predBoxDeltaOffsetW = {0, 0, 2};
    Eigen::array<int, 3> predBoxDeltaOffsetH = {0, 0, 3};

    Eigen::array<int, 3> predBoxDeltaExtent = {predBoxDelta.dimension(0), predBoxDelta.dimension(1), 1};

    Eigen::Tensor<float, 3> boxDeltaX = predBoxDelta.slice(predBoxDeltaOffsetX, predBoxDeltaExtent);
    Eigen::Tensor<float, 3> boxDeltaY = predBoxDelta.slice(predBoxDeltaOffsetY, predBoxDeltaExtent);
    Eigen::Tensor<float, 3> boxDeltaW = predBoxDelta.slice(predBoxDeltaOffsetW, predBoxDeltaExtent);
    Eigen::Tensor<float, 3> boxDeltaH = predBoxDelta.slice(predBoxDeltaOffsetH, predBoxDeltaExtent);


    //Eigen::Tensor<float, 2> s(1, );
//        Eigen::array<int, 4> networkReshape{{batchSize,
//                                                    aHeigth,
//                                                    aWidth,
//                                                    (int) tPred->size() / batchSize / aHeigth / aWidth}};
    // e.reshape()

    __android_log_print(ANDROID_LOG_VERBOSE, "APPNAME", "TEST %f", boxDeltaY(0, 5, 0));
}
