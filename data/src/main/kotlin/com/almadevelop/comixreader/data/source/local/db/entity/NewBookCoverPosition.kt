package com.almadevelop.comixreader.data.source.local.db.entity

import androidx.room.ColumnInfo
import com.almadevelop.comixreader.data.entity.ComicBook

/**
 * @param coverPosition image position in the comic book container.
 */
data class NewBookCoverPosition(
    @ColumnInfo(name = ComicBook.COLUMN_ID)
    val id: Long,
    @ColumnInfo(name = ComicBook.COLUMN_COVER_POSITION)
    val coverPosition: Long
)