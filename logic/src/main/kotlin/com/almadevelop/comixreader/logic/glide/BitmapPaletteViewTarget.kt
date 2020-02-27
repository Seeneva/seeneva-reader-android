package com.almadevelop.comixreader.logic.glide

import android.graphics.drawable.Drawable
import android.view.View
import com.almadevelop.comixreader.logic.ImageLoaderTarget
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition

/**
 * Target to use with [BitmapPalette]
 */
internal class BitmapPaletteViewTarget<T>(view: T) :
    CustomViewTarget<T, BitmapPalette>(view) where T : View, T : ImageLoaderTarget<BitmapPalette> {
    override fun onLoadFailed(errorDrawable: Drawable?) {
        view.onImageStateChanged(errorDrawable, ImageLoaderTarget.State.Fail)
    }

    override fun onResourceCleared(placeholder: Drawable?) {
        view.onImageStateChanged(placeholder, ImageLoaderTarget.State.Clear)
    }

    override fun onResourceReady(resource: BitmapPalette, transition: Transition<in BitmapPalette>?) {
        view.onImageLoaded(resource)
    }

    override fun onResourceLoading(placeholder: Drawable?) {
        super.onResourceLoading(placeholder)
        view.onImageStateChanged(placeholder, ImageLoaderTarget.State.Loading)
    }
}