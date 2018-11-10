#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/log.h>
#include <unsupported/Eigen/CXX11/Tensor>

using Eigen::Tensor;

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_almadevelop_comixreader_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */,
        jobject bitmap) {

    AndroidBitmapInfo info;
    int ret;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        //LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        //return NULL;
    }

    u_char *buffer;

    AndroidBitmap_lockPixels(env, bitmap, (void **) &buffer);

    Tensor<float, 3> imageTensor(info.height, info.width, 3);

    for (int row = 0; row < info.height; row++) {
        for (int col = 0; col < info.stride; col += 4) {
            const int startPosition = row * info.stride + col;
            for (int i = 0; i < 3; i++) {
                //Eigen::TensorMap<Eigen::Tensor<int,3>> mapped(v.data(), 3, 3, 3 ); !try it!
                imageTensor(row, col / 4, i) = buffer[startPosition + i];
            }
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    Tensor<float, 0> imgMean = imageTensor.mean();

    Tensor<float, 3> im = imageTensor - imageTensor.constant(imgMean(0));

    Tensor<float, 0> imgSTD = (im.square().sum() / (float) imageTensor.size()).sqrt();

    const Tensor<float, 3> imgNorm = im / im.constant(imgSTD(0));


    const jclass floatArrayCls = env->FindClass("[F");

    const jobjectArray hArray = env->NewObjectArray(info.height, env->FindClass("[[F"), NULL);

    for (int h = 0; h < imgNorm.dimension(0); h++) {
        jobjectArray wArray = env->NewObjectArray(info.width, floatArrayCls, NULL);
        for (int w = 0; w < imgNorm.dimension(1); w++) {
            jfloatArray vArray = env->NewFloatArray(3);
            float a[3];

            for (int v = 0; v < imgNorm.dimension(2); v++) {
                a[v] = imgNorm(h, w, v);
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

extern "C" JNIEXPORT void JNICALL
Java_com_almadevelop_comixreader_MainActivity_parsePrediction(
        JNIEnv *env,
        jobject /* this */,
        jobjectArray pred,
        jint cCount,
        jint batchSize,
        jint aHeigth,
        jint aWidth,
        jint aPerGrid) {
    //1 - backgound, 4 - bozes
    const int totalOutputCount = cCount + 1 + 4;
    //number of class probabilities, n classes for each anchor
    const int numClassProbs = aPerGrid * cCount;

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
        Eigen::array<int, 4> networkReshape{{batchSize,
                                                    aHeigth,
                                                    aWidth,
                                                    (int) tPred->size() / batchSize / aHeigth / aWidth}};

        Tensor<float, 4> t = tPred->reshape(networkReshape);

        // slice pred tensor to extract class pred scores and then normalize them
        //Eigen::array<int, 4> t1 = {0, 0, 0, 0};
        //Eigen::array<int, 4> t2 = {0, 0, 0, 0};
        Tensor<float, 1> e = t.chip(0, 0);

        __android_log_print(ANDROID_LOG_VERBOSE, "APPNAME", "TEST %i", t.dimension(3));
    }
}
