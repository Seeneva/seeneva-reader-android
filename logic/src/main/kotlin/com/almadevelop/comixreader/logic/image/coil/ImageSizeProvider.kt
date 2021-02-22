package com.almadevelop.comixreader.logic.image.coil

import android.view.View
import coil.size.*
import com.almadevelop.comixreader.logic.image.ImageSize
import com.almadevelop.comixreader.logic.image.ImageSizeProvider

/**
 * Use this [View] as [ImageSizeProvider]
 * @param subtractPadding If true, the view's padding will be subtracted from its size.
 */
fun View.asImageSizeProvider(subtractPadding: Boolean = true) =
    object : ImageSizeProvider {
        override suspend fun size() =
            ImageSize(ViewSizeResolver(this@asImageSizeProvider, subtractPadding).size())
    }

/**
 * Convert [ImageSizeProvider] into Coil's [SizeResolver]
 */
internal fun ImageSizeProvider.asSizeResolver() = object : SizeResolver {
    override suspend fun size() = this@asSizeResolver.size().innerSize
}