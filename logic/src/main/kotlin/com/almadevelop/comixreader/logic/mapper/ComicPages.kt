package com.almadevelop.comixreader.logic.mapper

import com.almadevelop.comixreader.data.entity.ComicBook
import com.almadevelop.comixreader.logic.entity.ComicBookDescription
import com.almadevelop.comixreader.logic.entity.ComicBookPage
import com.almadevelop.comixreader.logic.entity.Direction
import com.almadevelop.comixreader.data.entity.ComicBookPage as ComicBookPageInner

/**
 * Mapper from [ComicBook] into [ComicBookDescription]
 */
internal typealias ComicBookIntoDescription = (book: ComicBook?, persisted: Boolean) -> ComicBookDescription?

/**
 * Map [ComicBook] into [ComicBookDescription]
 */
internal fun ComicBook?.intoDescription(persisted: Boolean) =
    this?.let { book ->
        val pages = ArrayList<ComicBookPage>(book.pages.size)

        //use first page if we cannot find read position
        var readPosition = 0

        book.pages.sortedBy { page -> page.name }
            .forEachIndexed { index, page ->
                //some comic book containers keep not sorted pages
                pages += page.intoComicBookPage()

                if (page.position == book.readPosition) {
                    readPosition = index
                }
            }

        ComicBookDescription(
            book.id,
            book.filePath,
            book.displayName,
            persisted,
            Direction.fromId(book.direction),
            readPosition,
            pages
        )
    }

/**
 * Map data layer comic book page into logic layer comic book page
 */
internal fun ComicBookPageInner.intoComicBookPage() =
    ComicBookPage(id, position, width, height)