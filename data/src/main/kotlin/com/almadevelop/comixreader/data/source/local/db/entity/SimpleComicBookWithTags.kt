package com.almadevelop.comixreader.data.source.local.db.entity

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Junction
import androidx.room.Relation
import com.almadevelop.comixreader.data.entity.ComicBook
import com.almadevelop.comixreader.data.entity.ComicTag
import com.almadevelop.comixreader.data.source.local.db.entity.TaggedComicBook
import org.threeten.bp.Instant

data class SimpleComicBookWithTags(
    @ColumnInfo(name = ComicBook.COLUMN_ID)
    val id: Long,
    @ColumnInfo(name = ComicBook.COLUMN_FILE_PATH)
    val filePath: Uri,
    @ColumnInfo(name = ComicBook.COLUMN_DISPLAY_NAME)
    val displayName: String,
    @ColumnInfo(name = ComicBook.COLUMN_COVER_POSITION)
    val coverPosition: Long,
    @ColumnInfo(name = ComicBook.COLUMN_ACTION_TIME)
    val actionTime: Instant,
    @Relation(
        parentColumn = ComicBook.COLUMN_ID,
        entityColumn = ComicTag.COLUMN_ID,
        associateBy = Junction(
            value = TaggedComicBook::class,
            parentColumn = TaggedComicBook.COLUMN_BOOK_ID,
            entityColumn = TaggedComicBook.COLUMN_TAG_ID
        )
    )
    val tags: List<ComicTag>
)