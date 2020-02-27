package com.almadevelop.comixreader.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tag of a comic book
 */
@Entity(tableName = ComicTag.TABLE_NAME)
data class ComicTag(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ID)
    val id: Long,
    @ColumnInfo(name = COLUMN_NAME)
    val name: String,
    @ColumnInfo(name = COLUMN_TYPE)
    val type: Int
) {
    companion object {
        internal const val TABLE_NAME = "comic_book_tag"

        internal const val COLUMN_ID = "id"
        internal const val COLUMN_NAME = "name"
        internal const val COLUMN_TYPE = "type"
    }
}