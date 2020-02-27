package com.almadevelop.comixreader.logic.glide.resource

import androidx.palette.graphics.Palette
import com.almadevelop.comixreader.logic.glide.BitmapPalette
import com.bumptech.glide.load.engine.Initializable
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.bitmap.BitmapResource

internal class BitmapPaletteResource(private val bitmapRes: BitmapResource, private val palette: Palette) :
    Resource<BitmapPalette>, Initializable by bitmapRes {
    override fun getResourceClass(): Class<BitmapPalette> {
        return BitmapPalette::class.java
    }

    override fun get(): BitmapPalette {
        return BitmapPalette(bitmapRes.get(), palette)
    }

    override fun getSize(): Int {
        return bitmapRes.size
    }

    override fun recycle() {
        bitmapRes.recycle()
    }
}