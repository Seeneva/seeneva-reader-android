package com.almadevelop.comixreader.logic.glide.transcoder

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import com.almadevelop.comixreader.logic.glide.BitmapPalette
import com.almadevelop.comixreader.logic.glide.resource.BitmapPaletteResource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder

internal class BitmapBitmapPaletteTranscoder(private val bitmapPool: BitmapPool) :
    ResourceTranscoder<Bitmap, BitmapPalette> {
    override fun transcode(toTranscode: Resource<Bitmap>, options: Options): Resource<BitmapPalette>? {
        return if (toTranscode is BitmapResource) {
            toTranscode
        } else {
            BitmapResource.obtain(toTranscode.get(), bitmapPool)
        }?.let {
            val palette = Palette.from(it.get())
                .maximumColorCount(8)
                .addFilter { _, hsl -> hsl[2] < .3f } //take only dark colors
                .generate()

            BitmapPaletteResource(it, palette)
        }
    }
}