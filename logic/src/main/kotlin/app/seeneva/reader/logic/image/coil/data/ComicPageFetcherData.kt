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

package app.seeneva.reader.logic.image.coil.data

import android.graphics.Rect
import android.net.Uri

/**
 * Model to load desired comic page
 * @param path path to the container
 * @param pagePosition position of the image in the container
 * @param region crop params
 */
@Suppress("DataClassPrivateConstructor")
internal data class ComicPageFetcherData private constructor(
    val path: Uri,
    val pagePosition: Long,
    val region: Rect? = null,
) {
    companion object {
        /**
         * Data needed to fetch comic book page thumbnail
         * @param path path to the container
         * @param pagePosition position of the image in the container
         */
        fun thumb(path: Uri, pagePosition: Long) =
            ComicPageFetcherData(path, pagePosition)

        /**
         * Data needed to fetch comic book page region
         * @param region crop params
         */
        fun region(
            path: Uri,
            pagePosition: Long,
            region: Rect? = null,
        ) = ComicPageFetcherData(
            path,
            pagePosition,
            region,
        )
    }
}

