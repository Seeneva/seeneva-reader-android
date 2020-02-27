package com.almadevelop.comixreader.logic

import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import com.almadevelop.comixreader.logic.glide.BitmapPalette

interface ImageLoader {
    /**
     * Start loading comic book thumbnail
     * @param comicBookPath path to the comic book file
     * @param pagePosition position of the thumbnail in the comic book archive file
     * @param target view where to show the result
     * @param placeholder placeholder to display while loading thumbnail
     * @param cornerRadius used to round result image corners
     */
    fun <T> pageThumbnail(
        comicBookPath: Uri,
        pagePosition: Long,
        target: T,
        placeholder: Drawable? = null,
        cornerRadius: CornerRadius = CornerRadius()
    ) where T : View, T : ImageLoaderTarget<BitmapPalette>

    fun cancel(target: View)
}

data class CornerRadius(
    val topLeftRadius: Float = .0f,
    val topRightRadius: Float = .0f,
    val bottomRightRadius: Float = .0f,
    val bottomLeftRadius: Float = .0f
) {
    init {
        require(topLeftRadius >= .0f)
        require(topRightRadius >= .0f)
        require(bottomRightRadius >= .0f)
        require(bottomLeftRadius >= .0f)
    }

    val hasRoundCorners: Boolean
        get() = topLeftRadius > .0f || topRightRadius > .0f || bottomLeftRadius > .0f || bottomRightRadius > .0f

    val equalCorners: Boolean
        get() = topLeftRadius == topRightRadius && topLeftRadius == bottomLeftRadius && topLeftRadius == bottomRightRadius
}

/**
 * Target of a [ImageLoader]
 *
 * @param T type of a loading image
 */
interface ImageLoaderTarget<T> {
    /**
     * Called when state of an image loader process has been changed
     * @param drawable optional drawable to display
     * @param state state of an image loader
     */
    fun onImageStateChanged(drawable: Drawable?, state: State)

    /**
     * Called when image has been loaded
     * @param obj loaded image object
     */
    fun onImageLoaded(obj: T)

    enum class State {
        Loading, Clear, Fail
    }
}
