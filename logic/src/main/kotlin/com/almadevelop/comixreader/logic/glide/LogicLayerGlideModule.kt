package com.almadevelop.comixreader.logic.glide

import android.content.Context
import android.graphics.Bitmap
import com.almadevelop.comixreader.data.entity.ComicImage
import com.almadevelop.comixreader.logic.glide.decoder.ComicImageBitmapDecoder
import com.almadevelop.comixreader.logic.glide.loader.ComicImgLoader
import com.almadevelop.comixreader.logic.glide.model.ComicPageModel
import com.almadevelop.comixreader.logic.glide.transcoder.BitmapBitmapPaletteTranscoder
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.LibraryGlideModule

@GlideModule
internal class LogicLayerGlideModule : LibraryGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.append(ComicPageModel::class.java, ComicImage::class.java, ComicImgLoader.Factory())
            .append(ComicImage::class.java, Bitmap::class.java, ComicImageBitmapDecoder(glide.bitmapPool))
            .register(Bitmap::class.java, BitmapPalette::class.java, BitmapBitmapPaletteTranscoder(glide.bitmapPool))
    }
}