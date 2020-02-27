package com.almadevelop.comixreader.data.source.local.db.entity

import androidx.room.ColumnInfo
import com.almadevelop.comixreader.data.entity.ComicBook

/**
 * Result of checking is comic book has specific tag or not
 * @param id comic book id
 * @param hasTag is comic book has tag or not
 * @see com.almadevelop.comixreader.data.source.local.db.dao.ComicBookSource.hasTag
 */
data class BookHasTag(
    @ColumnInfo(name = ComicBook.COLUMN_ID)
    val id: Long,
    @ColumnInfo(name = "has_tag")
    val hasTag: Boolean
)