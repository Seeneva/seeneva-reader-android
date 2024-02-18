/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
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

package app.seeneva.reader.logic.entity

import android.net.Uri
import app.seeneva.reader.data.entity.ComicPageImageData as ComicPageImageDataInner

/**
 * Wrapper around inner encoded image
 * [inner] should be closed after use!
 *
 * @param path comic book container path
 * @param position comic book page position
 * @param inner wrapper around data layer
 */
data class ComicEncodedImage(
    val path: Uri,
    val position: Long,
    internal val inner: ComicPageImageDataInner
) {
    val width
        get() = inner.width
    val height
        get() = inner.height
}