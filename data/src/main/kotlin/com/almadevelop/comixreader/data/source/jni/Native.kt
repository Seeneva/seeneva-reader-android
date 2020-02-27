package com.almadevelop.comixreader.data.source.jni

import androidx.annotation.AnyThread
import com.almadevelop.comixreader.common.entity.FileHashData
import com.almadevelop.comixreader.data.entity.ComicBook
import com.almadevelop.comixreader.data.entity.ComicImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * All available native functions
 */
internal object Native {
    init {
        System.loadLibrary("comix_tensors")
    }

    /**
     * Native call to open comic book by provided file descriptor
     *
     * Run on background thread
     *
     * @param fd file descriptor of comic book file
     * @param filePath path to the file
     * @param displayName comic book display name
     * @param callback result callback
     * @return return task which can be used for [Task.cancel]
     */
    @AnyThread
    @JvmStatic
    external fun openComicBook(
        fd: Int,
        filePath: String,
        displayName: String,
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
     * Native call to get image by it [imagePosition]
     *
     * @param fd file descriptor of comic book file
     * @param imagePosition position of image in the comic book container
     * @param callback result callback
     * @return return task which can be used for [Task.cancel]
     */
    @AnyThread
    @JvmStatic
    external fun getImage(fd: Int, imagePosition: Long, callback: Callback<ComicImage>): Task

    /**
     * Native call to get image by it [imagePosition] and downscale it to the desired size.
     * This function doesn't change aspect ration of the image. So result image's size can be slightly different
     *
     * @param fd file descriptor of comic book file
     * @param imagePosition position of image in the comic book container
     * @param width desired image width
     * @param height desired image height
     * @param callback result callback
     * @return return task which can be used for [Task.cancel]
     */
    @AnyThread
    @JvmStatic
    external fun getImageThumbnail(
        fd: Int,
        imagePosition: Long,
        width: Int,
        height: Int,
        callback: Callback<ComicImage>
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
        @Volatile
        private var id: Long = EMPTY_PTR

        private val cancelled = AtomicBoolean(false)

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

        /**
         * Call to cancel this task
         *
         * @return is task was cancelled. false may indicate empty task or if cancellation is already in progress
         * @throws com.almadevelop.comixreader.data.NativeFatalError in case of any native error
         */
        @AnyThread
        fun cancel(): Boolean {
            return if (!cancelled.getAndSet(true) && id != EMPTY_PTR) {
                cancelNative()
            } else {
                false
            }
        }

        @AnyThread
        external fun cancelNative(): Boolean

        private companion object {
            private const val EMPTY_PTR = 0L
        }
    }
}