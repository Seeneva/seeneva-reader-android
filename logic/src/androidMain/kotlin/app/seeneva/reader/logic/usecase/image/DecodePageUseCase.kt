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

package app.seeneva.reader.logic.usecase.image

import android.graphics.Bitmap
import android.graphics.Rect
import app.seeneva.reader.data.source.jni.NativeSource
import app.seeneva.reader.logic.entity.ComicEncodedImage
import kotlinx.coroutines.CancellationException
import org.tinylog.kotlin.Logger

internal interface DecodePageUseCase {
    /**
     * Decode comic book page into provided bitmap. It can be optionally cropped and resized
     *
     * @param image encoded comic book page received from [GetEncodedPageUseCase]
     * @param bitmap target bitmap which will be filled with decoded pixels from source [image]
     * @param crop optional crop param
     * @param resizeFast pass true to enable fast resizing. But output image can be less accurate
     */
    suspend fun decodePageIntoBitmap(
        image: ComicEncodedImage,
        bitmap: Bitmap,
        crop: Rect? = null,
        resizeFast: Boolean = false
    )
}

internal class DecodePageUseCaseImpl(
    private val nativeSource: NativeSource,
) : DecodePageUseCase {
    override suspend fun decodePageIntoBitmap(
        image: ComicEncodedImage,
        bitmap: Bitmap,
        crop: Rect?,
        resizeFast: Boolean
    ) {
        if (crop != null) {
            require(crop.left >= 0 && crop.top >= 0 && crop.right >= 0 && crop.bottom >= 0) {
                "Cannot decode page. Invalid crop param: '$crop'"
            }
        }

        require(!image.inner.closed) { "Source encoded image cannot be closed" }

        return runCatching {
            nativeSource.decodePage(
                image.inner,
                bitmap,
                crop,
                resizeFast
            )
        }.onFailure {
            if (it !is CancellationException) {
                Logger.error(
                    it,
                    "Can't decode book page. Image: $image, size: ${bitmap.width}x${bitmap.height}, crop: $crop, color model: ${bitmap.config}"
                )
            }
        }.getOrThrow()
    }
}