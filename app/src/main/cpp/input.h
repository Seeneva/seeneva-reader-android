//
// Created by zellius on 11.11.18.
//

#ifndef ANDROID_COMIX_READER_TENSORFLOW_H
#define ANDROID_COMIX_READER_TENSORFLOW_H

#include <jni.h>
#include <android/bitmap.h>
#include <unsupported/Eigen/CXX11/Tensor>

using Eigen::Tensor;

const Tensor<float, 3> bitmapToTensor(JNIEnv *env, const jobject bitmap) {
    AndroidBitmapInfo info;
    int ret;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        //LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        //return NULL;
    }

    const u_char *buffer;

    AndroidBitmap_lockPixels(env, bitmap, (void **) &buffer);

    Tensor<float, 3> imageTensor(info.height, info.width, 3);

    for (int row = 0; row < info.height; row++) {
        for (int col = 0; col < info.stride; col += 4) {
            const int startPosition = row * info.stride + col;
            for (int i = 0; i < 3; i++) {
                //Eigen::TensorMap<Eigen::Tensor<int,3>> mapped(v.data(), 3, 3, 3 ); !try it!
                imageTensor(row, col / 4, i) = buffer[startPosition + 2 - i];
            }
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    return imageTensor;
}

const Tensor<float, 3> preprocessImageTensor(Tensor<float, 3> imageTensor) {
    float imgMean = ((Tensor<float, 0>) imageTensor.mean())(0);

    Tensor<float, 3> im = imageTensor - imgMean;

    Tensor<float, 0> imgSTD = (im.square().sum() / (float) imageTensor.size()).sqrt();

    const Tensor<float, 3> imgNorm = im / imgSTD(0);

    return imgNorm;
}

const jobjectArray imageTensorToJavaArray(JNIEnv *env, Tensor<float, 3> imageTensor) {
    const jclass floatArrayCls = env->FindClass("[F");

    const jobjectArray hArray = env->NewObjectArray((jsize) imageTensor.dimension(0), env->FindClass("[[F"), NULL);

    for (int h = 0; h < imageTensor.dimension(0); h++) {
        jobjectArray wArray = env->NewObjectArray((jsize) imageTensor.dimension(1), floatArrayCls, NULL);
        for (int w = 0; w < imageTensor.dimension(1); w++) {
            jfloatArray vArray = env->NewFloatArray(3);
            float a[3];

            for (int v = 0; v < imageTensor.dimension(2); v++) {
                a[v] = imageTensor(h, w, v);
            }

            env->SetFloatArrayRegion(vArray, 0, 3, a);
            env->SetObjectArrayElement(wArray, w, vArray);
            env->DeleteLocalRef(vArray);
        }
        env->SetObjectArrayElement(hArray, h, wArray);
        env->DeleteLocalRef(wArray);
    }

    return hArray;
}


#endif //ANDROID_COMIX_READER_TENSORFLOW_H
