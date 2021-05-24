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

package app.seeneva.reader.data.source.local.db.dao

import android.net.Uri
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import app.seeneva.reader.common.entity.FileHashData
import app.seeneva.reader.data.entity.ComicBook
import app.seeneva.reader.data.entity.FindResult
import app.seeneva.reader.data.source.local.db.ComicDatabase
import app.seeneva.reader.data.source.local.db.entity.*
import app.seeneva.reader.data.source.local.db.entity.TaggedComicBook
import app.seeneva.reader.data.source.local.db.query.CountQueryParams
import app.seeneva.reader.data.source.local.db.query.QueryParams
import app.seeneva.reader.data.source.local.db.query.intoCountSQLiteQuery
import app.seeneva.reader.data.source.local.db.query.intoSQLiteQuery
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import kotlin.coroutines.coroutineContext

@Dao
abstract class ComicBookSource internal constructor(private val database: ComicDatabase) {
    /**
     * Insert or replace comic books into database
     * @param comicBooks comic books to insert
     * @return inserted comic book ids
     */
    suspend fun insertOrReplace(vararg comicBooks: ComicBook): List<Long> {
        if (comicBooks.isEmpty()) {
            return emptyList()
        }

        val ids = ArrayList<Long>(comicBooks.size)

        database.withTransaction {
            comicBooks.forEach { comicBook ->
                coroutineContext.ensureActive()

                val comicBookId = insertInner(comicBook)

                ids += comicBookId

                comicBook.metadata?.also {
                    database.comicRackMetadataSource()
                        .insertOrReplace(it.copy(bookId = comicBookId))
                }

                if (comicBook.pages.isNotEmpty()) {
                    database.comicBookPageSource()
                        .insertOrReplace(comicBook.pages.map { it.copy(bookId = comicBookId) })
                }
            }
        }

        return ids
    }

    /**
     * Get simple comic books by provided [queryParams]
     * @param queryParams describes comic book query
     */
    suspend fun querySimpleWithTags(queryParams: QueryParams = QueryParams.EMPTY): List<SimpleComicBookWithTags> {
        return querySimpleWithTagsInner(queryParams.intoSQLiteQuery(SIMPLE_BOOK_COLUMNS.trimIndent()))
    }

    /**
     * Create simple comic book [Flow] by provided [queryParams]
     * @param queryParams describes comic book query
     */
    fun subscribeSimpleWithTags(queryParams: QueryParams = QueryParams.EMPTY): Flow<List<SimpleComicBookWithTags>> {
        return subscribeSimpleWithTagsInner(queryParams.intoSQLiteQuery(SIMPLE_BOOK_COLUMNS.trimIndent()))
    }

    /**
     * Try to get comic book by it [hashData] OR [path]
     * @param path comic book path
     * @param hashData comic book file data (size, hash, etc...)
     * @return comic book if it can be found. It can has different path or file description. So check it.
     */
    suspend fun findByContentOrPath(path: Uri, hashData: FileHashData): FindResult? =
        findByContentOrPath(path, hashData.hash, hashData.size)

    /**
     * @param queryParams params to use for count calculation
     * @return number of all saved comic books
     */
    suspend fun count(queryParams: CountQueryParams = CountQueryParams.EMPTY): Long =
        queryCountInner(queryParams.intoCountSQLiteQuery())

    /**
     * Edit comic book tags
     */
    suspend fun editTags(edit: EditTagsBuilder.() -> Unit) {
        val editActions = EditTagsBuilderImpl().apply(edit).actions

        if (editActions.isEmpty()) {
            return
        }

        database.withTransaction {
            editActions[TagAction.REMOVE]?.forEach { (bookId, tagIds) ->
                removeTags(bookId, tagIds)
            }

            editActions[TagAction.ADD]?.forEach { (bookId, tagIds) ->
                addTags(bookId, tagIds)
            }
        }
    }

    /**
     * Check is comic books has provided tag id
     * @see hasTag
     * @see hasTagInner
     */
    suspend fun hasTag(bookIds: Set<Long>, tagId: Long): List<BookHasTag> {
        return when (bookIds.size) {
            0 -> emptyList()
            else -> hasTagInner(bookIds, tagId)
        }
    }

    /**
     * Check is comic books has provided tag id
     * @param bookId comic book id to check
     * @param tagId comic book tag id to check
     * @return may return null if there is no such comic book
     */
    suspend fun hasTag(bookId: Long, tagId: Long): Boolean? {
        return hasTagInner(setOf(bookId), tagId).firstOrNull()?.hasTag
    }

    /**
     * Add tags to a comic book
     * @param bookId comic book id which should be updated
     * @param tagIds id of tags to add
     */
    suspend fun addTags(bookId: Long, tagIds: Set<Long>) =
        addTags(setOf(bookId), tagIds)

    /**
     * Add tags to a comic books
     * @param bookIds comic book ids which should be updated
     * @param tagIds id of tags to add
     */
    suspend fun addTags(bookIds: Set<Long>, tagIds: Set<Long>) =
        changeTags(bookIds, tagIds, true)

    /**
     * Remove tags from a comic book
     * @param bookId comic book id which should be updated
     * @param tagIds id of tags to remove
     */
    suspend fun removeTags(bookId: Long, tagIds: Set<Long>) =
        removeTags(setOf(bookId), tagIds)

    /**
     * Remove tags from a comic books
     * @param bookIds comic book ids which should be updated
     * @param tagIds id of tags to remove
     */
    suspend fun removeTags(bookIds: Set<Long>, tagIds: Set<Long>) =
        changeTags(bookIds, tagIds, false)

    /**
     * Change tags in a comic books
     * @param bookIds comic books id which should be updated
     * @param tagIds id of tags to add
     */
    suspend fun changeTags(
        bookIds: Set<Long>,
        tagIds: Set<Long>,
        add: Boolean
    ) {
        if (bookIds.isEmpty() && tagIds.isEmpty()) {
            return
        }

        val toChange = ArrayList<TaggedComicBook>(bookIds.size * tagIds.size).also {
            bookIds.forEach { bookId ->
                tagIds.forEach { tagId ->
                    it += TaggedComicBook(bookId, tagId)
                }
            }
        }

        if (add) {
            database.taggedComicBookSource().insert(toChange)
        } else {
            database.taggedComicBookSource().removeTags(toChange)
        }
    }

    /**
     * Get full comic book data by it [id]
     * @param id comic book id
     * @return full comic book data
     */
    suspend fun getFullById(id: Long): FullComicBookWithTags? {
        return getFullByIdInner(id).intoPublic()
    }

    /**
     * Subscribe to comic book updates by it [id]
     * @param id comic book id
     */
    fun subscribeFullById(id: Long): Flow<FullComicBookWithTags?> =
        subscribeFullByIdInner(id).map { it.intoPublic() }

    /**
     * Get comic book paths which have any of provided [tagIds]
     * @param tagIds requested comic book tag ids
     */
    suspend fun pathByTag(tagIds: Set<Long>): MutableSet<Uri> =
        pathByTagInner(tagIds).toHashSet()

    /**
     * Get comic book ids which have any of provided [tagIds]
     * @param tagIds requested comic book tag ids
     */
    suspend fun idByTag(tagIds: Set<Long>): MutableSet<Long> =
        idByTagInner(tagIds).toHashSet()

    /**
     * Update comic book path
     */
    suspend fun updatePath(bookId: Long, newPath: Uri) {
        updatePathInner(NewBookPath(bookId, newPath))
    }

    /**
     * Update comic book title
     */
    suspend fun updateTitle(bookId: Long, newTitle: String) {
        updateTitleInner(NewBookTitle(bookId, newTitle))
    }

    /**
     * Update comic book action time
     */
    suspend fun updateActionTime(bookId: Long, newActionTime: Instant = Instant.now()) {
        updateActionTimeInner(NewBookActionTime(bookId, newActionTime))
    }

    /**
     * Update comic book cover position
     */
    suspend fun updateCoverPosition(bookId: Long, newCoverPosition: Long) {
        updateCoverPositionInner(NewBookCoverPosition(bookId, newCoverPosition))
    }

    /**
     * Update comic book read position
     */
    suspend fun updateReadPosition(bookId: Long, newReadPosition: Long) {
        updateReadPositionInner(NewBookReadPosition(bookId, newReadPosition))
    }

    /**
     * Update comic book read direction
     */
    suspend fun updateDirection(bookId: Long, newDirection: Int) {
        updateDirectionInner(NewBookDirection(bookId, newDirection))
    }

    /**
     * Subscribe on comic book read direction changes
     * @param bookId target book id
     */
    @Query("""
        SELECT ${ComicBook.COLUMN_DIRECTION} FROM ${ComicBook.TABLE_NAME}
        WHERE ${ComicBook.COLUMN_ID} == (:bookId)
    """)
    abstract fun subscribeOnDirection(bookId: Long): Flow<Int>

    /**
     * Get comic book paths by it ids
     * @param bookIds comic book ids
     * @return comic book paths by provided ids
     */
    @Query(
        """
        SELECT ${ComicBook.COLUMN_FILE_PATH}
        FROM ${ComicBook.TABLE_NAME}
        WHERE ${ComicBook.COLUMN_ID} IN (:bookIds)
    """
    )
    abstract suspend fun pathById(bookIds: Set<Long>): List<Uri>

    /**
     * Delete comic books by it ids
     * @param bookIds comic book's ids to remove
     */
    @Query(
        """
        DELETE FROM ${ComicBook.TABLE_NAME} 
        WHERE ${ComicBook.COLUMN_ID} IN (:bookIds)
    """
    )
    abstract suspend fun delete(bookIds: Set<Long>): Int

    /**
     * Delete comic books by tags
     * @param tagIds tag ids
     * @return count of deleted comic books
     */
    @Query(
        """
        DELETE FROM ${ComicBook.TABLE_NAME}
        WHERE ${ComicBook.COLUMN_ID} IN (
            SELECT ${TaggedComicBook.COLUMN_BOOK_ID} FROM ${TaggedComicBook.TABLE_NAME}
            WHERE ${TaggedComicBook.COLUMN_TAG_ID} IN (:tagIds)
        )
    """
    )
    abstract suspend fun deleteByTag(tagIds: Set<Long>): Int

    /**
     * Update comic book path
     */
    @Update(entity = ComicBook::class, onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun updatePathInner(newPath: NewBookPath)

    /**
     * Update comic book title
     */
    @Update(entity = ComicBook::class, onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun updateTitleInner(newTitle: NewBookTitle)

    /**
     * Update comic book action time
     */
    @Update(entity = ComicBook::class, onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun updateActionTimeInner(newTime: NewBookActionTime)

    /**
     * Update comic book cover position
     */
    @Update(entity = ComicBook::class, onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun updateCoverPositionInner(newCoverPosition: NewBookCoverPosition)

    /**
     * Update comic book read position
     */
    @Update(entity = ComicBook::class, onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun updateReadPositionInner(newReadPosition: NewBookReadPosition)

    /**
     * Update comic book read direction
     */
    @Update(entity = ComicBook::class, onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun updateDirectionInner(newDirection: NewBookDirection)

    @Query(
        """
        SELECT DISTINCT ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_FILE_PATH}
        FROM ${ComicBook.TABLE_NAME}
            LEFT JOIN ${TaggedComicBook.TABLE_NAME} ON ${TaggedComicBook.COLUMN_BOOK_ID} = ${ComicBook.COLUMN_ID}
        WHERE ${TaggedComicBook.TABLE_NAME}.${TaggedComicBook.COLUMN_TAG_ID} IN (:tagIds)
    """
    )
    protected abstract suspend fun pathByTagInner(tagIds: Set<Long>): List<Uri>

    /**
     * Get comic book ids by provided tags
     */
    @Query(
        """
        SELECT DISTINCT ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_ID}
        FROM ${ComicBook.TABLE_NAME}
            LEFT JOIN ${TaggedComicBook.TABLE_NAME} ON ${TaggedComicBook.COLUMN_BOOK_ID} = ${ComicBook.COLUMN_ID}
        WHERE ${TaggedComicBook.TABLE_NAME}.${TaggedComicBook.COLUMN_TAG_ID} IN (:tagIds)
    """
    )
    protected abstract suspend fun idByTagInner(tagIds: Set<Long>): List<Long>

    /**
     * Check is provided [bookIds] has tag with specific [tagId]. Result can be empty if there is no such comic books!
     * @param bookIds comic book ids to check
     * @param tagId comic book tag to check
     * @return check result. Length can be less than input [bookIds]
     */
    @Query(
        """
        SELECT ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_ID}, 
	        CASE
	        WHEN COUNT(${TaggedComicBook.TABLE_NAME}.${TaggedComicBook.COLUMN_TAG_ID}) > 0 THEN 1
	        ELSE 0
	        END has_tag
        FROM ${ComicBook.TABLE_NAME} 
        LEFT JOIN ${TaggedComicBook.TABLE_NAME}
            ON ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_ID} = ${TaggedComicBook.TABLE_NAME}.${TaggedComicBook.COLUMN_BOOK_ID} 
            AND ${TaggedComicBook.TABLE_NAME}.${TaggedComicBook.COLUMN_TAG_ID} = :tagId
        WHERE ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_ID} IN (:bookIds) 
        GROUP BY ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_ID}
    """
    )
    protected abstract suspend fun hasTagInner(
        bookIds: Set<Long>,
        tagId: Long
    ): List<BookHasTag>

    /**
     * Get full comic book data by it id
     * @param id comic book id
     */
    @Transaction
    @Query("SELECT * FROM ${ComicBook.TABLE_NAME} WHERE ${ComicBook.COLUMN_ID} = :id LIMIT 1")
    protected abstract suspend fun getFullByIdInner(id: Long): FullComicBookWithTagsInner?

    @Transaction
    @Query("SELECT * FROM ${ComicBook.TABLE_NAME} WHERE ${ComicBook.COLUMN_ID} = :id LIMIT 1")
    protected abstract fun subscribeFullByIdInner(id: Long): Flow<FullComicBookWithTagsInner?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertInner(comicBook: ComicBook): Long

    @Query(
        """
        SELECT $SIMPLE_BOOK_COLUMNS,
        CASE
            WHEN ${ComicBook.COLUMN_FILE_HASH} = :fileHash AND ${ComicBook.COLUMN_FILE_SIZE} = :fileSize THEN ${FindResult.SQL_BY_CONTENT}
            ELSE ${FindResult.SQL_BY_PATH}
            END ${FindResult.COLUMN_FOUND_TYPE}
        FROM ${ComicBook.TABLE_NAME}
        WHERE (${ComicBook.COLUMN_FILE_HASH} = :fileHash AND ${ComicBook.COLUMN_FILE_SIZE} = :fileSize) OR 
        ${ComicBook.COLUMN_FILE_PATH} = :path
        LIMIT 1
    """
    )
    @Transaction
    protected abstract suspend fun findByContentOrPath(
        path: Uri,
        fileHash: ByteArray,
        fileSize: Long
    ): FindResult?

    @RawQuery
    protected abstract suspend fun querySimpleWithTagsInner(query: SupportSQLiteQuery): List<SimpleComicBookWithTags>

    @RawQuery(observedEntities = [ComicBook::class, TaggedComicBook::class])
    protected abstract fun subscribeSimpleWithTagsInner(query: SupportSQLiteQuery): Flow<List<SimpleComicBookWithTags>>

    @RawQuery
    protected abstract suspend fun queryCountInner(query: SupportSQLiteQuery): Long

    internal enum class TagAction { ADD, REMOVE }

    interface EditTagsBuilder {
        fun addTags(bookId: Long, tagIds: Set<Long>)

        fun removeTags(bookId: Long, tagIds: Set<Long>)
    }

    private class EditTagsBuilderImpl : EditTagsBuilder {
        val actions: Map<TagAction, Map<Long, Set<Long>>>
            get() = actionsInner

        private val actionsInner = hashMapOf<TagAction, MutableMap<Long, Set<Long>>>()

        override fun addTags(bookId: Long, tagIds: Set<Long>) {
            addAction(TagAction.ADD, bookId, tagIds)
        }

        override fun removeTags(bookId: Long, tagIds: Set<Long>) {
            addAction(TagAction.REMOVE, bookId, tagIds)
        }

        private fun addAction(actionType: TagAction, bookId: Long, tagIds: Set<Long>) {
            if (tagIds.isEmpty()) {
                return
            }

            actionsInner.getOrPut(actionType) { hashMapOf() }[bookId] = tagIds
        }
    }

    companion object {
        //I can't trim it because it used inside Room's annotations too
        //So it should be const at compile time
        private const val SIMPLE_BOOK_COLUMNS = """
            ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_ID},
            ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_FILE_PATH},
            ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_DISPLAY_NAME},
            ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_COVER_POSITION},
            ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_ACTION_TIME},
            ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_DIRECTION}
        """
    }
}

