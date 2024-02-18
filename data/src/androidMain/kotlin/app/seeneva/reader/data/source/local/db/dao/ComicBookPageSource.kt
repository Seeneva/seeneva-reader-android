/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
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

package app.seeneva.reader.data.source.local.db.dao

import androidx.room.*
import app.seeneva.reader.data.entity.ComicBookPage
import app.seeneva.reader.data.source.local.db.ComicDatabase
import app.seeneva.reader.data.source.local.db.entity.ComicPageObjectsData
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

@Dao
abstract class ComicBookPageSource internal constructor(private val database: ComicDatabase) {
    /**
     * Insert or replace provided comic book pages
     * @param pages pages to insert
     * @return pages ids
     */
    suspend fun insertOrReplace(pages: List<ComicBookPage>): List<Long> {
        val ids = ArrayList<Long>(pages.size)

        database.withTransaction {
            pages.forEach { page ->
                coroutineContext.ensureActive()

                val pageId = insertOrReplaceInner(page)

                ids += pageId

                if (page.objects.isNotEmpty()) {
                    database.comicBookPageObjectSource()
                        .insertOrReplace(page.objects.map { it.copy(pageId = pageId) })
                }
            }
        }

        return ids
    }

    /**
     * @param id comic book page id
     * @param classIds optional required ML objects classes. Pass empty to retrieve all objects on the page
     * @return comic book page ML objects if there is such page.
     */
    suspend fun objectsDataById(id: Long, classIds: Set<Long> = emptySet()): ComicPageObjectsData? =
        database.withTransaction {
            baseObjectsDataByIdInner(id)?.let {
                //I don't use relations inside POJO to have ability filter objects by classId
                val objects = database.comicBookPageObjectSource().getObjects(id, classIds)

                if (objects.isNotEmpty()) {
                    it.copy(objects = objects)
                } else {
                    it
                }
            }
        }

    /**
     * @param id comic book page id
     * @return comic book page base data if there is such page. Without ML objects
     */
    @Transaction
    @Query(
        """
            SELECT ${ComicBookPage.COLUMN_ID}, ${ComicBookPage.COLUMN_POSITION}, ${ComicBookPage.COLUMN_BOOK_ID} 
            FROM ${ComicBookPage.TABLE_NAME} 
            WHERE ${ComicBookPage.COLUMN_ID} = :id 
            LIMIT 1
        """
    )
    protected abstract suspend fun baseObjectsDataByIdInner(id: Long): ComicPageObjectsData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertOrReplaceInner(page: ComicBookPage): Long
}