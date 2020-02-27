package com.almadevelop.comixreader

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.module.AppGlideModule

@GlideModule
class AppComicGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        super.applyOptions(context, builder)

        builder.setDiskCache(InternalCacheDiskCacheFactory(context, 1024 * 1024 * 20)) //20 mb
    }

    override fun isManifestParsingEnabled(): Boolean = false
}