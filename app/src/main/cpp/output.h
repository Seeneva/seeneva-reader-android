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

Tensor<float, 4, Eigen::RowMajor> parsePredictions(JNIEnv *env, jobjectArray &pred, PredictionInfo &predInfo) {
    const int totalOutputCount = getTotalOutputCount(predInfo.classCount);

    Tensor<float, 3, Eigen::RowMajor> t;

    Tensor<float, 3, Eigen::RowMajor> tt;

    jsize firstArrayLength = env->GetArrayLength(pred);

    for (int i = 0; i < firstArrayLength; i++) {
        jobjectArray secondArray = static_cast<jobjectArray>(env->GetObjectArrayElement(pred, i));
        jsize secondArrayLength = env->GetArrayLength(secondArray);

        for (int j = 0; j < secondArrayLength; j++) {
            jfloatArray jArray = static_cast<jfloatArray>(env->GetObjectArrayElement(secondArray, j));
            jfloat *ar = env->GetFloatArrayElements(jArray, (jboolean *) JNI_FALSE);

            if (t.data() == NULL) {
                t.resize(firstArrayLength, secondArrayLength, totalOutputCount);
                tt.resize(firstArrayLength, secondArrayLength, totalOutputCount);
            }

            for (int z = 0; z < totalOutputCount; z++) {
                t(i, j, z) = ar[z];
                tt(i, j, z) = ar[z];
            }

            env->ReleaseFloatArrayElements(jArray, ar, 0);

            env->DeleteLocalRef(jArray);
        }

        env->DeleteLocalRef(secondArray);
    }

    if (t.data() == NULL) {
        jclass errClass = env->FindClass("java/lang/Error");
        env->ThrowNew(errClass, "Can't get access to tensor data.");
        env->DeleteLocalRef(errClass);
    } else {
        array<int, 4> networkReshape{{predInfo.batchSize,
                                             predInfo.aHeigth,
                                             predInfo.aWidth,
                                             (int) t.size() /
                                             predInfo.batchSize /
                                             predInfo.aHeigth /
                                             predInfo.aWidth}};

        float s = t(0, 0, 0);
        float ss = t(0, 0, 1);
        float sss = t(0, 0, 2);
        float ssss = t(0, 0, 3);
        float sssss = t(0, 0, 4);

        float p = t(0, 1, 0);
        float pp = t(0, 1, 1);
        float ppp = t(0, 1, 2);
        float pppp = t(0, 1, 3);
        float ppppp = t(0, 1, 4);

        Eigen::Tensor<float, 4, Eigen::RowMajor> e = tt.reshape(networkReshape);

        float z = e(0, 0, 0, 0);
        float zz = e(0, 0, 0, 1);
        float zzz = e(0, 0, 0, 2);
        float zzzz = e(0, 0, 0, 3);
        float zzzzz = e(0, 0, 0, 4);


        return t.reshape(networkReshape);
    }
}

#endif //ANDROID_COMIX_READER_OUTPUT_H
