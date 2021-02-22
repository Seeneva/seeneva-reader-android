package com.almadevelop.comixreader.data.source.local.db.entity

import androidx.room.ColumnInfo
import com.almadevelop.comixreader.data.entity.ComicBook

/**
 * Set new comic book direction
 * @param id comic book id
 * @param direction comic book direction to set
 */
data class NewBookDirection(
    @ColumnInfo(name = ComicBook.COLUMN_ID)
    val id: Long,
    @ColumnInfo(name = ComicBook.COLUMN_DIRECTION)
    val direction: Int
)