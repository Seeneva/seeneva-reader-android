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

package app.seeneva.reader.logic.image.coil

import android.view.View
import coil.size.*
import app.seeneva.reader.logic.image.ImageSize
import app.seeneva.reader.logic.image.ImageSizeProvider

/**
 * Use this [View] as [ImageSizeProvider]
 * @param subtractPadding If true, the view's padding will be subtracted from its size.
 */
fun View.asImageSizeProvider(subtractPadding: Boolean = true) =
    object : ImageSizeProvider {
        override suspend fun size() =
            ImageSize(ViewSizeResolver(this@asImageSizeProvider, subtractPadding).size())
    }

/**
 * Convert [ImageSizeProvider] into Coil's [SizeResolver]
 */
internal fun ImageSizeProvider.asSizeResolver() = object : SizeResolver {
    override suspend fun size() = this@asSizeResolver.size().innerSize
}