package com.almadevelop.comixreader.data.source.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.almadevelop.comixreader.data.entity.ComicBook
import com.almadevelop.comixreader.data.entity.ComicTag

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