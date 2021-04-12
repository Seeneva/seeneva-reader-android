/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.seeneva.reader.data.source.local.db.entity

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.Relation
import app.seeneva.reader.data.entity.ComicBook
import app.seeneva.reader.data.entity.ComicBookPage
import app.seeneva.reader.data.entity.ComicPageObject

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