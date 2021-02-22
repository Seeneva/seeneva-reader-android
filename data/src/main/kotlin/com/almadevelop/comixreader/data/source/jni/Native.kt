package com.almadevelop.comixreader.data.source.jni

import android.content.res.AssetManager
import android.graphics.Bitmap
import androidx.annotation.AnyThread
import androidx.annotation.FloatRange
import com.almadevelop.comixreader.common.entity.FileHashData
import com.almadevelop.comixreader.data.entity.ComicBook
import com.almadevelop.comixreader.data.entity.ComicPageImageData
import com.almadevelop.comixreader.data.entity.ml.Interpreter
import com.almadevelop.comixreader.data.entity.ml.Tesseract
import java.util.concurrent.atomic.AtomicBoolean

/**
 * All available native functions
 */
internal object Native {
    init {
        System.loadLibrary("comix_tensors")
    }

    /**
     * Native call to init ML Interpreter from Android assets by [modelAssetName]
     *
     * Will be executed on background thread
     *
     * @param assetManager Android asset manager
     * @param modelAssetName ML model asset name
     * @param callback result callback
     * @return return task which can be used for [Task.cancel]
     */
    @AnyThread
    @JvmStatic
    external fun initInterpreterFromAsset(
        assetManager: AssetManager,
        modelAssetName: String,
        callback: Callback<Interpreter>
    ): Task

    /**
     * Native call to init Tesseract from Android assets by [tessDataName]
     *
     * Will be executed on background thread
     *
     * @param assetManager Android asset manager
     * @param tessDataName Tesseract data asset name
     * @param tessDataLang Tesseract data language
     * @param callback result callback
     * @return return task which can be used for [Task.cancel]
     */
    @AnyThread
    @JvmStatic
    external fun initTesseractFromAsset(
        assetManager: AssetManager,
        tessDataName: String,
        tessDataLang: String,
        callback: Callback<Tesseract>
    ): Task

    /**
     * Native call to open comic book by provided file descriptor
     *
     * Run on background thread
     *
     * @param fd file descriptor of comic book file
     * @param filePath path to the file
     * @param displayName comic book display name
     * @param direction comic book read direction
     * @param interpreter ML interpreter
     * @param callback result callback
     * @return return task which can be used for [Task.cancel]
     */
    @AnyThread
    @JvmStatic
    external fun openComicBook(
        fd: Int,
        filePath: String,
        displayName: String,
        direction: Int,
        interpreter: Interpreter,
        callback: Callback<ComicBook>
    ): Task

    /**
     * Native call to get comic book file data
     *
     * @param fd file descriptor of comic book file
     * @return comic book file data
     */
    @JvmStatic
    external fun getComicFileData(fd: Int): FileHashData

    /**
     * Native call to get comic book page image data by it [position]
     *
     * @param fd file descriptor of comic book file
     * @param position position of image in the comic book container
     * @param callback result callback
     * @return return task which can be used for [Task.cancel]
     */
    @AnyThread
    @JvmStatic
    external fun getPageImageData(
        fd: Int,
        position: Long,
        callback: Callback<ComicPageImageData>
    ): Task

    /**
     * Decode image received using [getPageImageData].
     *
     * @param pageImageData source image which will be decoded
     * @param bitmap target [Bitmap]
     * * Source image will be resized to the dimensions of the bitmap
     * * Supported color spaces: [Bitmap.Config.ARGB_8888] and [Bitmap.Config.RGB_565]
     * @param crop
     * * pass null to disable cropping
     * * pass 4 values to crop image [x, y, width, height]
     * @param resizeFast pass true to enable fast resizing. But output image can be less accurate
     * @param callback result callback
     * @return return task which can be used for [Task.cancel]
     */
    @AnyThread
    @JvmStatic
    external fun decodePage(
        pageImageData: ComicPageImageData,
        bitmap: Bitmap,
        crop: IntArray?,
        resizeFast: Boolean,
        callback: Callback<Unit>
    ): Task

    /**
     * Recognise text on provided [Bitmap]. Will return empty [String] in case if no text was found
     *
     * @param tesseract tesseract instance
     * @param bitmap source bitmap where text should be recognised
     * @param wordMinConf minimal confidence for each recognised word
     * @param callback result callback
     * @return return task which can be used for [Task.cancel]
     */
    @AnyThread
    @JvmStatic
    external fun recogniseText(
        tesseract: Tesseract,
        bitmap: Bitmap,
        @FloatRange(from = 0.0, to = 1.0) wordMinConf: Float,
        callback: Callback<String>
    ): Task

    /**
     * Callback for native functions which proceed on background thread
     *
     * @param T type of result
     */
    interface Callback<T> {
        /**
         * Called than result is ready
         * Non UI thread!
         *
         * @param result result of native function
         */
        fun taskResult(result: T)

        /**
         * Called than error appears in the native functions
         * Non UI thread!
         *
         * @param error error object
         */
        fun taskError(error: Throwable)
    }

    /**
     * Native task. Used primary for cancellation
     */
    class Task private constructor() {
        /**
         * Set from JNI. Pointer to the native object that used to [cancel]
         */
        @Suppress("unused")
        private var id: Long = NULL_PTR

        private val cancelled = AtomicBoolean(false)

        /**
         * Call to cancel this task
         *
         * @return is task was cancelled. false may indicate empty task or if cancellation is already in progress
         * @throws com.almadevelop.comixreader.data.NativeFatalError in case of any native error
         */
        @AnyThread
        fun cancel(): Boolean {
            return if (cancelled.compareAndSet(false, true)) {
                cancelNative()
            } else {
                false
            }
        }

        @AnyThread
        private external fun cancelNative(): Boolean

        override fun toString() = "Task(id=$id, cancelled=${cancelled.get()})"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Task

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        private companion object {
            private const val NULL_PTR = 0L
        }
    }
}