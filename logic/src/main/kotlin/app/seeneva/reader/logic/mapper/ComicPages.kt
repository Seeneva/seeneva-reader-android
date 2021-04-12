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

package app.seeneva.reader.logic.mapper

import app.seeneva.reader.data.entity.ComicBook
import app.seeneva.reader.logic.entity.ComicBookDescription
import app.seeneva.reader.logic.entity.ComicBookPage
import app.seeneva.reader.logic.entity.Direction
import app.seeneva.reader.data.entity.ComicBookPage as ComicBookPageInner

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