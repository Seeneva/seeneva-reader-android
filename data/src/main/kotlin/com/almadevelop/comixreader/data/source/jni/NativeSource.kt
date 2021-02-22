package com.almadevelop.comixreader.data.source.jni

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.FloatRange
import com.almadevelop.comixreader.common.entity.FileHashData
import com.almadevelop.comixreader.data.entity.ComicBook
import com.almadevelop.comixreader.data.entity.ComicPageImageData
import com.almadevelop.comixreader.data.entity.ml.Interpreter
import com.almadevelop.comixreader.data.entity.ml.Tesseract
import com.almadevelop.comixreader.data.source.jni.Native.Task
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger
import kotlin.coroutines.Continuation
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
     * @throws com.almadevelop.comixreader.data.NativeFatalError
     * @throws com.almadevelop.comixreader.data.NativeException
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
     * @throws com.almadevelop.comixreader.data.NativeFatalError
     * @throws com.almadevelop.comixreader.data.NativeException
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
     * @throws com.almadevelop.comixreader.data.NativeFatalError
     * @throws com.almadevelop.comixreader.data.NativeException
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
        withContext(dispatcher) {
            suspendCancellableCoroutine<Interpreter> { cont ->
                val task = Native.initInterpreterFromAsset(
                    context.assets,
                    modelAssetName,
                    BaseCallback(cont)
                )

                cont.invokeOnCancellation { task.cancel() }
            }
        }

    override suspend fun initTesseractFromAsset(tessDataName: String, tessDataLang: String) =
        withContext(dispatcher) {
            suspendCancellableCoroutine<Tesseract> { cont ->
                val task = Native.initTesseractFromAsset(
                    context.assets,
                    tessDataName,
                    tessDataLang,
                    BaseCallback(cont)
                )

                cont.invokeOnCancellation { task.cancel() }
            }
        }

    override suspend fun getComicsMetadata(
        path: Uri,
        initName: String,
        direction: Int,
        interpreter: Interpreter
    ): ComicBook {
        require(!interpreter.closed) { "Interpreter cannot be closed" }

        return withContext(dispatcher) {
            cancellableTask(path) { fd, cont ->
                Native.openComicBook(
                    fd,
                    path.toString(),
                    initName,
                    direction,
                    interpreter,
                    BaseCallback(cont)
                )
            }
        }
    }

    override suspend fun getComicFileData(path: Uri): FileHashData =
        withContext(dispatcher) {
            Native.getComicFileData(path.detachFd())
        }

    override suspend fun getPageImageData(path: Uri, position: Long): ComicPageImageData =
        withContext(dispatcher) {
            cancellableTask(path) { fd, cont ->
                Native.getPageImageData(
                    fd,
                    position,
                    BaseCallback(cont)
                )
            }
        }

    override suspend fun decodePage(
        pageImageData: ComicPageImageData,
        bitmap: Bitmap,
        crop: Rect?,
        resizeFast: Boolean
    ) = withContext(dispatcher) {
        require(!pageImageData.closed) { "Comic book page image data cannot be closed" }

        suspendCancellableCoroutine<Unit> { cont ->
            val task = Native.decodePage(
                pageImageData,
                bitmap,
                crop?.let { intArrayOf(it.left, it.top, it.width(), it.height()) },
                resizeFast,
                BaseCallback(cont)
            )

            cont.invokeOnCancellation { task.cancel() }
        }
    }

    override suspend fun recognizeText(
        tesseract: Tesseract,
        bitmap: Bitmap,
        wordMinConf: Float
    ): String {
        require(!tesseract.closed) { "Tesseract cannot be closed" }
        require(!bitmap.isRecycled) { "Text cannot be recognized on already recycled bitmap" }

        return suspendCancellableCoroutine { cont ->
            val task = Native.recogniseText(
                tesseract,
                bitmap,
                wordMinConf,
                BaseCallback(cont)
            )

            cont.invokeOnCancellation { task.cancel() }
        }
    }

    /**
     * Create cancellable native task
     * @param path path to a comic book to open
     * @param f function used to work with received file descriptor
     */
    private suspend inline fun <T> cancellableTask(
        path: Uri,
        crossinline f: (fd: Int, cont: Continuation<T>) -> Task
    ): T {
        return suspendCancellableCoroutine { cont ->
            val task = f(path.detachFd(), cont)

            cont.invokeOnCancellation { task.cancel() }
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

    private class BaseCallback<T>(private val cont: Continuation<T>) : Native.Callback<T> {
        override fun taskResult(result: T) {
            cont.resume(result)
        }

        override fun taskError(error: Throwable) {
            Logger.error(error, "Task error")
            cont.resumeWithException(error)
        }
    }
}