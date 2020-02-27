package com.almadevelop.comixreader.data.source.local.db.entity

import androidx.room.ColumnInfo
import com.almadevelop.comixreader.data.entity.ComicBook

/**
 * Used to update comic book title
 */
data class NewBookTitle(
    @ColumnInfo(name = ComicBook.COLUMN_ID)
    val id: Long,
    @ColumnInfo(name = ComicBook.COLUMN_DISPLAY_NAME)
    val newTitle: String
)