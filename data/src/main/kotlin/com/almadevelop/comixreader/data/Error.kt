package com.almadevelop.comixreader.data

import androidx.annotation.Keep

/**
 * Fatal errors. E.g. JNI errors
 */
@Suppress("unused")
@Keep
class NativeFatalError(message: String) : Error(message)

@Keep
@Suppress("unused")
class NativeException(val code: Int, message: String? = null) : RuntimeException(message) {
    companion object {
        @JvmStatic
        @Keep
        val CODE_CONTAINER_READ = 0

        /**
         * Known format, but it can't be used as a comic container
         */
        @JvmStatic
        @Keep
        val CODE_CONTAINER_OPEN_UNSUPPORTED = 1

        /**
         * Comic book archive doesn't contain any images
         */
        @JvmStatic
        @Keep
        val CODE_EMPTY_BOOK = 2

        /**
         * Can't open one or more image in the comic book archive
         */
        @JvmStatic
        @Keep
        val CODE_IMAGE_OPEN = 3

        /**
         * Can't find the file in the comic book container (e.g. comic page by it position)
         */
        @JvmStatic
        @Keep
        val CODE_CONTAINER_CANT_FIND_FILE = 4
    }
}

