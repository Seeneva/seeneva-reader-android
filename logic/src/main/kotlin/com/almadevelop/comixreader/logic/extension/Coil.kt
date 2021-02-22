package com.almadevelop.comixreader.logic.extension

import android.graphics.Bitmap
import coil.decode.Options
import coil.request.ImageRequest
import coil.request.get

private const val PARAM_BITMAP_CFG = "force_bitmap_cfg"
private const val PARAM_FAST_RESIZING = "allow_fast_resizing"

/**
 * Set bitmap config which should be forced by a fetcher. Do not use it if you use any transitions
 */
internal fun ImageRequest.Builder.forceBitmapConfig(config: Bitmap.Config) {
    bitmapConfig(config)
    setParameter(PARAM_BITMAP_CFG, config, null)
}

/**
 * Allow fast resizing if required. Output image can be less accurate
 */
internal fun ImageRequest.Builder.allowFastResizing() {
    setParameter(PARAM_FAST_RESIZING, true)
}

/**
 * Get bitmap config which was set as `forced` if any
 */
internal val Options.forcedBitmapConfig
    get() = parameters[PARAM_BITMAP_CFG] as? Bitmap.Config

/**
 * Is fast resizing allowed
 */
internal val Options.allowFastResizing
    get() = parameters[PARAM_FAST_RESIZING] as? Boolean ?: false