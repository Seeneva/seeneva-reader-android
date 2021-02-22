package com.almadevelop.comixreader.logic.usecase.image

import android.graphics.Bitmap
import android.graphics.Rect
import com.almadevelop.comixreader.data.source.jni.NativeSource
import com.almadevelop.comixreader.logic.entity.ComicEncodedImage
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