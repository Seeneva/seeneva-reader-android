package com.almadevelop.comixreader.logic.glide

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import com.almadevelop.comixreader.logic.CornerRadius
import com.almadevelop.comixreader.logic.ImageLoader
import com.almadevelop.comixreader.logic.ImageLoaderTarget
import com.almadevelop.comixreader.logic.glide.model.PageThumbnailModel
import com.almadevelop.comixreader.logic.glide.transformation.DifferentRoundedCorners
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.CenterInside

/**
 * Glide implementation of the app [ImageLoader]
 */
internal class GlideImageLoader(private val glideRequest: RequestManager) : ImageLoader {
    override fun <T> pageThumbnail(
        comicBookPath: Uri,
        pagePosition: Long,
        target: T,
        placeholder: Drawable?,
        cornerRadius: CornerRadius
    ) where T : View, T : ImageLoaderTarget<BitmapPalette> {
        glideRequest.`as`(BitmapPalette::class.java)
            .format(DecodeFormat.PREFER_RGB_565)
            .load(PageThumbnailModel(comicBookPath, pagePosition))
            .placeholder(placeholder)
            .also {
                val transformations = arrayListOf<Transformation<Bitmap>>(CenterInside())

                if (cornerRadius.hasRoundCorners) {
                    transformations += DifferentRoundedCorners(cornerRadius)
                }

                it.transform(*transformations.toTypedArray())
            }
            .into(BitmapPaletteViewTarget(target))
    }

    override fun cancel(target: View) {
        glideRequest.clear(target)
    }
}