package com.almadevelop.comixreader.logic.entity

import android.net.Uri
import com.almadevelop.comixreader.data.entity.ComicPageImageData as ComicPageImageDataInner

/**
 * Wrapper around inner encoded image
 * [inner] should be closed after use!
 *
 * @param path comic book container path
 * @param position comic book page position
 * @param inner wrapper around data layer
 */
data class ComicEncodedImage(
    val path: Uri,
    val position: Long,
    internal val inner: ComicPageImageDataInner
) {
    val width
        get() = inner.width
    val height
        get() = inner.height
}