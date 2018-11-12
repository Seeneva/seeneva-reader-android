//
// Created by zellius on 11.11.18.
//

#ifndef ANDROID_COMIX_READER_OUTPUT_H
#define ANDROID_COMIX_READER_OUTPUT_H

#include <jni.h>
#include <unsupported/Eigen/CXX11/Tensor>

using Eigen::Tensor;
using Eigen::array;

struct PredictionInfo {
    int classCount;
    int batchSize;
    int anchors;
    int aHeigth;
    int aWidth;
    int aPerGrid;
};

//1 - backgound, 4 - bozes
int getTotalOutputCount(int classCount) {
    return classCount + 1 + 4;
}

Tensor<float, 4> parsePredictions(JNIEnv *env, jobjectArray pred, PredictionInfo predInfo) {
    const int totalOutputCount = getTotalOutputCount(predInfo.classCount);

    Tensor<float, 3> *tPred = 0;

    jsize firstArrayLength = env->GetArrayLength(pred);

    for (int i = 0; i < firstArrayLength; i++) {
        jobjectArray secondArray = static_cast<jobjectArray>(env->GetObjectArrayElement(pred, i));
        jsize secondArrayLength = env->GetArrayLength(secondArray);

        for (int j = 0; j < secondArrayLength; j++) {
            jfloatArray jArray = static_cast<jfloatArray>(env->GetObjectArrayElement(secondArray, j));
            jfloat *ar = env->GetFloatArrayElements(jArray, (jboolean *) JNI_FALSE);

            if (tPred == 0) {
                Tensor<float, 3> predTensor(firstArrayLength, secondArrayLength, totalOutputCount);
                tPred = &predTensor;
            }

            for (int z = 0; z < totalOutputCount; z++) {
                tPred->operator()(i, j, z) = ar[z];
            }

            env->ReleaseFloatArrayElements(jArray, ar, 0);

            env->DeleteLocalRef(jArray);
        }

        env->DeleteLocalRef(secondArray);
    }

    if (tPred == 0) {
        jclass errClass = env->FindClass("java/lang/Error");
        env->ThrowNew(errClass, "Can't get access to tensor data.");
        env->DeleteLocalRef(errClass);
    } else {
        array<int, 4> networkReshape{{predInfo.batchSize,
                                             predInfo.aHeigth,
                                             predInfo.aWidth,
                                             (int) tPred->size() /
                                             predInfo.batchSize /
                                             predInfo.aHeigth /
                                             predInfo.aWidth}};

        return tPred->reshape(networkReshape);
    }
}

#endif //ANDROID_COMIX_READER_OUTPUT_H
