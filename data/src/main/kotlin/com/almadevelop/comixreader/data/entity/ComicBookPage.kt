package com.almadevelop.comixreader.data.entity

import androidx.annotation.Keep
import androidx.room.*

/**
 * Metadata of one single page in the comic book
 * @param position page position in the comic container
 * @param name page file displayName
 * @param width comic page width
 * @param height comic page height
 */
@Entity(
    tableName = ComicBookPage.TABLE_NAME,
    foreignKeys = [ForeignKey(
        entity = ComicBook::class,
        parentColumns = [ComicBook.COLUMN_ID],
        childColumns = [ComicBookPage.COLUMN_BOOK_ID],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(
        name = ComicBookPage.INDEX_BOOK_ID_POSITION,
        value = [ComicBookPage.COLUMN_BOOK_ID, ComicBookPage.COLUMN_POSITION],
        unique = true
    )]
)
data class ComicBookPage(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ID)
    val id: Long,
    @ColumnInfo(name = COLUMN_BOOK_ID)
    val bookId: Long,
    @ColumnInfo(name = COLUMN_POSITION)
    val position: Long,
    @ColumnInfo(name = COLUMN_NAME)
    val name: String,
    @ColumnInfo(name = COLUMN_WIDTH)
    val width: Int,
    @ColumnInfo(name = COLUMN_HEIGHT)
    val height: Int
) {

    /**
     * Called from a native side
     */
    @Suppress("unused")
    @Keep
    @Ignore
    internal constructor(
        position: Long,
        name: String,
        width: Int,
        height: Int
    ) : this(0, 0, position, name, width, height)

    companion object {
        internal const val TABLE_NAME = "comic_book_page"

        internal const val COLUMN_ID = "id"
        internal const val COLUMN_BOOK_ID = "book_id"
        internal const val COLUMN_POSITION = "position"
        internal const val COLUMN_NAME = "name"
        internal const val COLUMN_WIDTH = "width"
        internal const val COLUMN_HEIGHT = "height"

        internal const val INDEX_BOOK_ID_POSITION = "idx_book_id_position"
    }
}