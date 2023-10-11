/*
 *  This file is part of Seeneva Android Reader
 *  Copyright (C) 2021-2023 Sergei Solodovnikov
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.seeneva.reader.data.entity

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
data class ComicBookPage @Ignore constructor(
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
    val height: Int,
    @Ignore
    val objects: List<ComicPageObject>
) {
    // for some reason @kotlin.jvm.JvmOverloads doesn't work anymore
    // https://issuetracker.google.com/issues/70762008
    /**
     * Used by room
     */
    internal constructor(
        id: Long,
        bookId: Long,
        position: Long,
        name: String,
        width: Int,
        height: Int
    ) : this(id, bookId, position, name, width, height, emptyList())

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
        height: Int,
        objects: Array<ComicPageObject>
    ) : this(0, 0, position, name, width, height, objects.asList())

    init {
        require(position >= 0L) { "Invalid position value $position" }
        require(width >= 0L) { "Invalid width value $width" }
        require(height >= 0L) { "Invalid width value $height" }
    }

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