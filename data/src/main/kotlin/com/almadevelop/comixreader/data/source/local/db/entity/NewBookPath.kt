package com.almadevelop.comixreader.data.source.local.db.entity

import android.net.Uri
import androidx.room.ColumnInfo
import com.almadevelop.comixreader.data.entity.ComicBook

/**
 * Used to update comic book path
 */
data class NewBookPath(
    @ColumnInfo(name = ComicBook.COLUMN_ID)
    val id: Long,
    @ColumnInfo(name = ComicBook.COLUMN_FILE_PATH)
    val path: Uri
)