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

package app.seeneva.reader.data.source.local.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import app.seeneva.reader.data.entity.ComicBook
import app.seeneva.reader.data.entity.ComicBookPage
import app.seeneva.reader.data.entity.ComicRackMetadata
import app.seeneva.reader.data.entity.ComicTag

/**
 * Result of Room query
 */
data class FullComicBookWithTagsInner(
    @Embedded
    val comicBook: ComicBook,
    @Relation(
        parentColumn = ComicBook.COLUMN_ID,
        entityColumn = ComicRackMetadata.COLUMN_BOOK_ID,
        entity = ComicRackMetadata::class
    )
    val metadataWithPages: ComicRackMetadataWithPages?,
    @Relation(
        parentColumn = ComicBook.COLUMN_ID,
        entityColumn = ComicBookPage.COLUMN_BOOK_ID,
        entity = ComicBookPage::class
    )
    val pages: List<ComicBookPageWithObjects>,
    @Relation(
        parentColumn = ComicBook.COLUMN_ID,
        entityColumn = ComicTag.COLUMN_ID,
        associateBy = Junction(
            value = TaggedComicBook::class,
            parentColumn = TaggedComicBook.COLUMN_BOOK_ID,
            entityColumn = TaggedComicBook.COLUMN_TAG_ID
        )
    )
    val tags: List<ComicTag>
)

/**
 * Comic book with it tags
 * @param comicBook comic book
 * @param tags comic book tags if any
 */
data class FullComicBookWithTags(val comicBook: ComicBook, val tags: List<ComicTag>)

/**
 * Map inner response to public
 */
internal fun FullComicBookWithTagsInner?.intoPublic(): FullComicBookWithTags? =
    this?.let {
        val comicBook = comicBook.copy(
            metadata = metadataWithPages.intoPublic(),
            pages = pages.map(ComicBookPageWithObjects::intoPublic)
        )
        FullComicBookWithTags(comicBook, tags)
    }