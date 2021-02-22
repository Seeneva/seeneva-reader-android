package com.almadevelop.comixreader.data.source.local.db.dao

import androidx.room.*
import com.almadevelop.comixreader.data.entity.ComicBookPage
import com.almadevelop.comixreader.data.source.local.db.ComicDatabase
import com.almadevelop.comixreader.data.source.local.db.entity.ComicPageObjectsData
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