/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
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

package app.seeneva.reader.logic.image

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