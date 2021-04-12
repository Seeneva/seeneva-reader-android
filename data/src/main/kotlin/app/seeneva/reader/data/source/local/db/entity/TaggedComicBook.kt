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

package app.seeneva.reader.data.source.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import app.seeneva.reader.data.entity.ComicBook
import app.seeneva.reader.data.entity.ComicTag

/**
 * Represents many-to-many join table between [ComicBook] and [ComicTag]
 */
@Entity(
    tableName = TaggedComicBook.TABLE_NAME,
    primaryKeys = [TaggedComicBook.COLUMN_BOOK_ID, TaggedComicBook.COLUMN_TAG_ID],
    foreignKeys = [
        ForeignKey(
            entity = ComicBook::class,
            parentColumns = [ComicBook.COLUMN_ID],
            childColumns = [TaggedComicBook.COLUMN_BOOK_ID],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ComicTag::class,
            parentColumns = [ComicTag.COLUMN_ID],
            childColumns = [TaggedComicBook.COLUMN_TAG_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = [TaggedComicBook.COLUMN_TAG_ID])]
)
internal data class TaggedComicBook(
    @ColumnInfo(name = COLUMN_BOOK_ID)
    val bookId: Long,
    @ColumnInfo(name = COLUMN_TAG_ID)
    val tagId: Long
) {
    companion object {
        internal const val TABLE_NAME = "tagged_comic_book"

        internal const val COLUMN_BOOK_ID = "book_id"
        internal const val COLUMN_TAG_ID = "tag_id"
    }
}