/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.seeneva.reader.data.source.jni

import android.content.res.AssetManager
import android.graphics.Bitmap
import androidx.annotation.AnyThread
import androidx.annotation.FloatRange
import androidx.annotation.Keep
import app.seeneva.reader.common.entity.FileHashData
import app.seeneva.reader.data.entity.ComicBook
import app.seeneva.reader.data.entity.ComicPageImageData
import app.seeneva.reader.data.entity.ml.Interpreter
import app.seeneva.reader.data.entity.ml.Tesseract
import java.util.concurrent.atomic.AtomicBoolean

/**
 * All available native functions
 */
internal object Native {
    init {
        System.loadLibrary("seeneva")
    }

    /**
     * Native call to init ML Interpreter from Android assets by [modelAssetName]
     *
     * Will be executed on background thread
     *
     * @param assetManager Android asset manager
     * @param modelAssetName ML model asset name
     * @param callback result callback
     * @return return task which can be used for [TaskHandler.close]
     */
    @AnyThread
    @JvmStatic
    external fun initInterpreterFromAsset(
        assetManager: AssetManager,
        modelAssetName: String,
        callback: Callback<Interpreter>
    ): TaskHandler

    /**
     * Native call to init Tesseract from Android assets by [tessDataName]
     *
     * Will be executed on background thread
     *
     * @param assetManager Android asset manager
     * @param tessDataName Tesseract data asset name
     * @param tessDataLang Tesseract data language
     * @param callback result callback
     * @return return task which can be used for [TaskHandler.close]
     */
    @AnyThread
    @JvmStatic
    external fun initTesseractFromAsset(
        assetManager: AssetManager,
        tessDataName: String,
        tessDataLang: String,
        callback: Callback<Tesseract>
    ): TaskHandler

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
     * @return return task which can be used for [TaskHandler.close]
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
    ): TaskHandler

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
     * @return return task which can be used for [TaskHandler.close]
     */
    @AnyThread
    @JvmStatic
    external fun getPageImageData(
        fd: Int,
        position: Long,
        callback: Callback<ComicPageImageData>
    ): TaskHandler

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
     * @return return task which can be used for [TaskHandler.close]
     */
    @AnyThread
    @JvmStatic
    external fun decodePage(
        pageImageData: ComicPageImageData,
        bitmap: Bitmap,
        crop: IntArray?,
        resizeFast: Boolean,
        callback: Callback<Unit>
    ): TaskHandler

    /**
     * Recognise text on provided [Bitmap]. Will return empty [String] in case if no text was found
     *
     * @param tesseract tesseract instance
     * @param bitmap source bitmap where text should be recognised
     * @param wordMinConf minimal confidence for each recognised word
     * @param callback result callback
     * @return return task which can be used for [TaskHandler.close]
     */
    @AnyThread
    @JvmStatic
    external fun recogniseText(
        tesseract: Tesseract,
        bitmap: Bitmap,
        @FloatRange(from = 0.0, to = 1.0) wordMinConf: Float,
        callback: Callback<String>
    ): TaskHandler

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
        @Keep
        fun taskResult(result: T)

        /**
         * Called than error appears in the native functions
         * Non UI thread!
         *
         * @param error error object
         */
        @Keep
        fun taskError(error: Throwable)
    }

    /**
     * Native task handler. Used primary for cancellation.
     * Should always be closed after a usage by calling [close] to free native resources.
     */
    class TaskHandler @Keep private constructor() {
        /**
         * Set from JNI. Pointer to the native object that used to [close] the object
         */
        @Suppress("unused")
        @Keep
        private var ptr: Long = NULL_PTR

        private val _closed = AtomicBoolean(false)

        val closed: Boolean
            get() = _closed.get()

        /**
         * Call it to cancel the task and clear native resources
         *
         * @return is task was closed. false may indicate empty task or if closing is already in progress
         * @throws app.seeneva.reader.data.NativeFatalError in case of any native error
         */
        @AnyThread
        fun close(): Boolean {
            return if (_closed.compareAndSet(false, true)) {
                closeNative()
            } else {
                false
            }
        }

        @AnyThread
        private external fun closeNative(): Boolean

        override fun toString() = "TaskHandler(ptr=$ptr, closed=${_closed.get()})"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TaskHandler

            if (ptr != other.ptr) return false

            return true
        }

        override fun hashCode(): Int {
            return ptr.hashCode()
        }

        private companion object {
            private const val NULL_PTR = 0L
        }
    }
}