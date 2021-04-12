/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.seeneva.reader.logic.extension

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