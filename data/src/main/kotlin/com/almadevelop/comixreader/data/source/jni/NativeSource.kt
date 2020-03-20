package com.almadevelop.comixreader.data.source.jni

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.almadevelop.comixreader.common.entity.FileHashData
import com.almadevelop.comixreader.data.entity.ComicBook
import com.almadevelop.comixreader.data.entity.ComicImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface NativeSource {
    /**
     * Get comic book metadata from provided uri
     * @param path comic book path
     * @param initName comic book init name. Can be changed if comic book archive has metadata with provided real name
     *
     * @return future of the task
     * @throws com.almadevelop.comixreader.data.NativeFatalError
     * @throws com.almadevelop.comixreader.data.NativeException
     */
    suspend fun getComicsMetadata(path: Uri, initName: String): ComicBook

    /**
     * Get comic book file data
     *
     * @param path comic book path
     * @return comic book file data
     */
    suspend fun getComicFileData(path: Uri): FileHashData

    /**
     * Native call to get image by it [imagePosition]. Image can be downscaled if [imageResize] was passed.
     * It doesn't change image aspect ratio.
     *
     * @param path path to the comic book file
     * @param imagePosition position of image in the comic book container
     * @param imageResize image resize type
     *
     * @throws com.almadevelop.comixreader.data.NativeFatalError
     * @throws com.almadevelop.comixreader.data.NativeException
     */
    suspend fun getImage(
        path: Uri,
        imagePosition: Long,
        imageResize: ImageResize = ImageResize.None
    ): ComicImage

    sealed class ImageResize {
        /**
         * Don't try to resize image
         */
        object None : ImageResize()

        /**
         * Downscale image as thumbnail
         */
        data class Thumbnail(val width: Int, val height: Int) : ImageResize()
    }
}

/**
 * Default implementation of the native source
 */
internal class NativeSourceImpl(
    context: Context,
    private val dispatcher: CoroutineDispatcher
) : NativeSource {
    private val context = context.applicationContext

    override suspend fun getComicsMetadata(path: Uri, initName: String) =
        withContext(dispatcher) {
            cancellableTask<ComicBook>(path) { fd, cont ->
                Native.openComicBook(
                    fd,
                    path.toString(),
                    initName,
                    BaseCallback(cont)
                )
            }
        }

    override suspend fun getComicFileData(path: Uri): FileHashData =
        withContext(dispatcher) {
            Native.getComicFileData(path.detachFd())
        }


    override suspend fun getImage(
        path: Uri,
        imagePosition: Long,
        imageResize: NativeSource.ImageResize
    ) = withContext(dispatcher) {
        cancellableTask<ComicImage>(path) { fd, cont ->
            val nativeCallback = BaseCallback(cont)

            when (imageResize) {
                is NativeSource.ImageResize.None -> Native.getImage(
                    fd,
                    imagePosition,
                    nativeCallback
                )
                is NativeSource.ImageResize.Thumbnail -> Native.getImageThumbnail(
                    fd,
                    imagePosition,
                    imageResize.width,
                    imageResize.height,
                    nativeCallback
                )
            }
        }
    }

    /**
     * Create cancellable native task
     * @param path path to a comic book to open
     * @param f function used to work with received file descriptor
     */
    private suspend inline fun <T> cancellableTask(
        path: Uri,
        crossinline f: (fd: Int, cont: Continuation<T>) -> Native.Task
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

    private data class BaseCallback<T>(private val cont: Continuation<T>) : Native.Callback<T> {
        override fun taskResult(result: T) {
            cont.resume(result)
        }

        override fun taskError(error: Throwable) {
            Logger.error(error)
            cont.resumeWithException(error)
        }
    }
}