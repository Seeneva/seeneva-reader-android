package com.almadevelop.comixreader.data.source.local.db.entity

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.Relation
import com.almadevelop.comixreader.data.entity.ComicBook
import com.almadevelop.comixreader.data.entity.ComicBookPage
import com.almadevelop.comixreader.data.entity.ComicPageObject

/**
 * Describes singe comic book page with founded ML objects
 *
 * @param id page id
 * @param position page position in the comic book container
 * @param bookId parent comic book id
 * @param bookPath source book path
 * @param objects founded ML objects
 */
data class ComicPageObjectsData @JvmOverloads constructor(
    @ColumnInfo(name = ComicBookPage.COLUMN_ID)
    val id: Long,
    @ColumnInfo(name = ComicBookPage.COLUMN_POSITION)
    val position: Long,
    @ColumnInfo(name = ComicBookPage.COLUMN_BOOK_ID)
    val bookId: Long,
    @Relation(
        entity = ComicBook::class,
        parentColumn = ComicBookPage.COLUMN_BOOK_ID,
        entityColumn = ComicBook.COLUMN_ID,
        projection = [ComicBook.COLUMN_FILE_PATH]
    )
    val bookPath: Uri,
    @Ignore
    val objects: List<ComicPageObject> = emptyList(),
)