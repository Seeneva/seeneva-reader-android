package com.almadevelop.comixreader.logic.image

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import com.almadevelop.comixreader.logic.image.coil.asImageSizeProvider
import com.almadevelop.comixreader.logic.image.entity.DrawablePalette
import com.almadevelop.comixreader.logic.image.target.ImageLoaderTarget

interface ImageLoader {
    /**
     * Start loading comic book thumbnail
     * @param comicBookPath path to the comic book file
     * @param pagePosition position of the thumbnail in the comic book archive file
     * @param target where to show the result
     * @param placeholder placeholder to display while loading thumbnail
     * @param cornerRadius used to round result image corners
     * @param sizeProvider image size provider
     */
    fun <T> pageThumbnail(
        comicBookPath: Uri,
        pagePosition: Long,
        target: T,
        placeholder: Drawable? = null,
        cornerRadius: CornerRadius = CornerRadius(),
        sizeProvider: ImageSizeProvider = target.asImageSizeProvider()
    ): ImageLoadingTask where T : View, T : ImageLoaderTarget<DrawablePalette>

    /**
     * Start loading comic book page preview for viewer
     * @param comicBookPath path to the comic book file
     * @param pagePosition position of the thumbnail in the comic book archive file
     * @param target view where to show the result
     * @param placeholder placeholder to display while loading thumbnail
     */
    fun viewerPreview(
        comicBookPath: Uri,
        pagePosition: Long,
        target: ImageView,
        placeholder: Drawable? = null
    ): ImageLoadingTask

    /**
     * Decode page with crop and resize options
     *
     * @param comicBookPath path to the comic book file
     * @param pagePosition position of a page in the comic book archive file
     * @param cropRegion image crop region. Output image will have this size if [size] is not provided
     * @param size target image size
     *
     * @return result bitmap
     */
    suspend fun decodeRegion(
        comicBookPath: Uri,
        pagePosition: Long,
        cropRegion: Rect? = null,
        size: ImageSize = ImageSize.original()
    ): Bitmap

    /**
     * Load page object image
     *
     * @param comicBookPath path to the comic book file
     * @param pagePosition position of a page in the comic book archive file
     * @param bbox object bounding box
     * @param target loading target. Pass null to prefetch object into memory
     * @param error drawable to show in case of error
     * @param onFinished called on success or error results
     */
    fun loadPageObject(
        comicBookPath: Uri,
        pagePosition: Long,
        bbox: Rect,
        target: ImageView? = null,
        error: Drawable? = null,
        onFinished: () -> Unit = {}
    ): ImageLoadingTask

    /**
     * Load page object image and return it as Android [Bitmap]
     *
     * @param comicBookPath path to the comic book file
     * @param pagePosition position of a page in the comic book archive file
     * @param bbox object bounding box
     *
     * @see loadPageObject
     */
    suspend fun loadPageObjectBitmap(
        comicBookPath: Uri,
        pagePosition: Long,
        bbox: Rect,
    ): Bitmap
}