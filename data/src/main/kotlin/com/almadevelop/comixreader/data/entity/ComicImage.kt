package com.almadevelop.comixreader.data.entity

import androidx.annotation.Keep

/**
 * Describes single image page from a comic book container
 *
 * @param imageColors RGBA decoded image colors
 * @param imageWidth width of the image
 * @param imageHeight height of the image
 */
@Suppress("unused")
@Keep
data class ComicImage(val imageColors: IntArray, val imageWidth: Int, val imageHeight: Int){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ComicImage

        if (!imageColors.contentEquals(other.imageColors)) return false
        if (imageWidth != other.imageWidth) return false
        if (imageHeight != other.imageHeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = imageColors.contentHashCode()
        result = 31 * result + imageWidth
        result = 31 * result + imageHeight
        return result
    }
}