package com.almadevelop.comixreader.logic.image.coil.data

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

