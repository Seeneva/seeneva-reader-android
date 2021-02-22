package com.almadevelop.comixreader.logic.image

import coil.size.OriginalSize
import coil.size.PixelSize
import coil.size.Size

/**
 * Used to provide target image size
 */
interface ImageSizeProvider {
    /**
     * @return calculated image size
     */
    suspend fun size(): ImageSize
}

/**
 * Image size used during image fetch
 */
class ImageSize internal constructor(internal val innerSize: Size) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageSize

        if (innerSize != other.innerSize) return false

        return true
    }

    override fun hashCode(): Int {
        return innerSize.hashCode()
    }

    override fun toString(): String {
        return "ImageSize(size=$innerSize)"
    }

    companion object {
        /**
         * Original image size
         */
        fun original() = ImageSize(OriginalSize)

        /**
         * Specific image size
         * @param width
         * @param height
         */
        fun specific(width: Int, height: Int) = ImageSize(PixelSize(width, height))
    }
}