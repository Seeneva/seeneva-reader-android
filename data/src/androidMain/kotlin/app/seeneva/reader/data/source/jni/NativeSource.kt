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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.FloatRange
import app.seeneva.reader.common.entity.FileHashData
import app.seeneva.reader.data.entity.ComicBook
import app.seeneva.reader.data.entity.ComicPageImageData
import app.seeneva.reader.data.entity.ml.Interpreter
import app.seeneva.reader.data.entity.ml.Tesseract
import app.seeneva.reader.data.source.jni.Native.TaskHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface NativeSource {
    /**
     * Init ML Interpreter from Android assets by [modelAssetName]
     *
     * @param modelAssetName ML model asset name
     *
     * @return ML interpreter
     */
    suspend fun initInterpreterFromAsset(modelAssetName: String): Interpreter

    /**
     * Init Tesseract from Android assets by [tessDataName]
     *
     * @param tessDataName Tesseract data asset name
     * @param tessDataLang Tesseract data language
     */
    suspend fun initTesseractFromAsset(tessDataName: String, tessDataLang: String): Tesseract

    /**
     * Get comic book metadata from provided uri
     * @param path comic book path
     * @param initName comic book init name. Can be changed if comic book archive has metadata with provided real name
     * @param direction comic book read direction
     * @param interpreter ML interpreter
     *
     * @return comic book metadata
     *
     * @throws app.seeneva.reader.data.NativeFatalError
     * @throws app.seeneva.reader.data.NativeException
     */
    suspend fun getComicsMetadata(
        path: Uri,
        initName: String,
        direction: Int,
        interpreter: Interpreter
    ): ComicBook

    /**
     * Get comic book file data
     *
     * @param path comic book path
     * @return comic book file data
     */
    suspend fun getComicFileData(path: Uri): FileHashData

    /**
     * Native call to get comic book page image data by it [position].
     *
     * @param path path to the comic book file
     * @param position position of image in the comic book container
     *
     * @throws app.seeneva.reader.data.NativeFatalError
     * @throws app.seeneva.reader.data.NativeException
     */
    suspend fun getPageImageData(path: Uri, position: Long): ComicPageImageData

    /**
     * Decode provided comic book page
     *
     * @param pageImageData encoded comic book page
     * @param bitmap target bitmap that will be filled with [pageImageData] decoded pixels
     * @param crop optional result comic book crop operation.
     * @param resizeFast pass true to enable fast resizing. But output image can be less accurate
     *
     * @throws app.seeneva.reader.data.NativeFatalError
     * @throws app.seeneva.reader.data.NativeException
     */
    suspend fun decodePage(
        pageImageData: ComicPageImageData,
        bitmap: Bitmap,
        crop: Rect? = null,
        resizeFast: Boolean = false,
    )

    /**
     * Recognize text on provided [Bitmap]
     *
     * @param tesseract tesseract instance
     * @param bitmap source bitmap where text should be recognised
     * @param wordMinConf minimal confidence for each recognised word
     * @return recognized text or empty [String] in case if text was not found
     */
    suspend fun recognizeText(
        tesseract: Tesseract,
        bitmap: Bitmap,
        @FloatRange(from = 0.0, to = 1.0) wordMinConf: Float = 0.4f
    ): String
}

/**
 * Default implementation of the native source
 */
internal class NativeSourceImpl(
    context: Context,
    private val dispatcher: CoroutineDispatcher
) : NativeSource {
    private val context = context.applicationContext

    override suspend fun initInterpreterFromAsset(modelAssetName: String) =
        suspendTask<Interpreter> { callback ->
            Native.initInterpreterFromAsset(
                context.assets,
                modelAssetName,
                callback
            )
        }

    override suspend fun initTesseractFromAsset(tessDataName: String, tessDataLang: String) =
        suspendTask<Tesseract> { callback ->
            Native.initTesseractFromAsset(
                context.assets,
                tessDataName,
                tessDataLang,
                callback
            )
        }

    override suspend fun getComicsMetadata(
        path: Uri,
        initName: String,
        direction: Int,
        interpreter: Interpreter
    ): ComicBook {
        require(!interpreter.closed) { "Interpreter cannot be closed" }

        return suspendTask { callback ->
            Native.openComicBook(
                path.detachFd(),
                path.toString(),
                initName,
                direction,
                interpreter,
                callback
            )
        }
    }

    override suspend fun getComicFileData(path: Uri): FileHashData =
        withContext(dispatcher) {
            Native.getComicFileData(path.detachFd())
        }

    override suspend fun getPageImageData(path: Uri, position: Long): ComicPageImageData =
        suspendTask { callback ->
            Native.getPageImageData(
                path.detachFd(),
                position,
                callback
            )
        }

    override suspend fun decodePage(
        pageImageData: ComicPageImageData,
        bitmap: Bitmap,
        crop: Rect?,
        resizeFast: Boolean
    ) {
        require(!pageImageData.closed) { "Comic book page image data cannot be closed" }

        return suspendTask { callback ->
            Native.decodePage(
                pageImageData,
                bitmap,
                crop?.let { intArrayOf(it.left, it.top, it.width(), it.height()) },
                resizeFast,
                callback
            )
        }
    }

    override suspend fun recognizeText(
        tesseract: Tesseract,
        bitmap: Bitmap,
        wordMinConf: Float
    ): String {
        require(!tesseract.closed) { "Tesseract cannot be closed" }
        require(!bitmap.isRecycled) { "Text cannot be recognized on already recycled bitmap" }

        return suspendTask { callback ->
            Native.recogniseText(
                tesseract,
                bitmap,
                wordMinConf,
                callback
            )
        }
    }

    /**
     * Suspend while provided [TaskHandler] is not completed (success or failure)
     * @param f provide a native [TaskHandler] using [Native.Callback]
     */
    private suspend inline fun <T> suspendTask(
        crossinline f: (callback: Native.Callback<T>) -> TaskHandler
    ): T {
        var task: TaskHandler? = null

        return withContext(dispatcher) {
            try {
                suspendCancellableCoroutine { cont ->
                    val callback = object : Native.Callback<T> {
                        override fun taskResult(result: T) {
                            cont.resume(result)
                        }

                        override fun taskError(error: Throwable) {
                            Logger.error(error, "Task error")
                            cont.resumeWithException(error)
                        }
                    }

                    task = f(callback)
                }
            } finally {
                // We should always clear task resources!
                task?.close()
            }
        }
    }

    /**
     * Detach Unix file descriptor. So it should be closed on a native side
     */
    private fun Uri.detachFd(): Int =
        fileDescriptor().detachFd()

    /**
     * Get a file descriptor from the provided [Uri]
     */
    private fun Uri.fileDescriptor(): ParcelFileDescriptor {
        return requireNotNull(
            context.contentResolver.openFileDescriptor(this, "r")
        ) { "Can't open Android file descriptor" }
    }
}