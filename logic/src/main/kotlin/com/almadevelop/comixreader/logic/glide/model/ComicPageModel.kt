package com.almadevelop.comixreader.logic.glide.model

import android.net.Uri

/**
 * Model used with Glide to load single image from container
 */
internal sealed class ComicPageModel {
    /**
     * path to the container
     */
    abstract val path: Uri
    /**
     * position of the image in the container
     */
    abstract val position: Long
}

/**
 * Model to load a thumbnail of the desired comic page
 */
internal data class PageThumbnailModel(
    override val path: Uri,
    override val position: Long
) : ComicPageModel()