package com.almadevelop.comixreader.data.source.local.db.entity

import androidx.room.ColumnInfo
import com.almadevelop.comixreader.data.entity.ComicBook

/**
 * @param readPosition image position in the comic book container
 */
data class NewBookReadPosition(
    @ColumnInfo(name = ComicBook.COLUMN_ID)
    val id: Long,
    @ColumnInfo(name = ComicBook.COLUMN_READ_POSITION)
    val readPosition: Long
)