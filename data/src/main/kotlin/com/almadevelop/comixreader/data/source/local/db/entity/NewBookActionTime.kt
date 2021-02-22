package com.almadevelop.comixreader.data.source.local.db.entity

import androidx.room.ColumnInfo
import com.almadevelop.comixreader.data.entity.ComicBook
import java.time.Instant

/**
 * Update comic book last action time
 * @param id comic book id
 * @param actionTime new action time to set
 */
data class NewBookActionTime(
    @ColumnInfo(name = ComicBook.COLUMN_ID)
    val id: Long,
    @ColumnInfo(name = ComicBook.COLUMN_ACTION_TIME)
    val actionTime: Instant
)