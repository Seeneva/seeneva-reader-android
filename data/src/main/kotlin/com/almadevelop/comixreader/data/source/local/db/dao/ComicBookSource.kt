package com.almadevelop.comixreader.data.source.local.db.dao

import android.net.Uri
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.almadevelop.comixreader.common.entity.FileHashData
import com.almadevelop.comixreader.data.entity.ComicBook
import com.almadevelop.comixreader.data.entity.FindResult
import com.almadevelop.comixreader.data.source.local.db.ComicDatabase
import com.almadevelop.comixreader.data.source.local.db.entity.*
import com.almadevelop.comixreader.data.source.local.db.entity.TaggedComicBook
import com.almadevelop.comixreader.data.source.local.db.query.CountQueryParams
import com.almadevelop.comixreader.data.source.local.db.query.QueryParams
import com.almadevelop.comixreader.data.source.local.db.query.intoCountSQLiteQuery
import com.almadevelop.comixreader.data.source.local.db.query.intoSQLiteQuery
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import java.util.*
import kotlin.collections.ArrayList
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

                if (!comicBook.pages.isNullOrEmpty()) {
                    database.comicBookPageSource()
                        .insert(comicBook.pages.map { it.copy(bookId = comicBookId) })
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
     * Try to get comic book by it [path] AND/OR [hashData]
     * @param path comic book path
     * @param hashData comic book file data (size, hash, etc...)
     * @return comic book if it can be found. It can has different path or file description. So check it.
     */
    suspend fun findByPathOrContent(path: Uri, hashData: FileHashData): FindResult? =
        findByPathOrContent(path, hashData.hash, hashData.size)

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
        addTags(Collections.singleton(bookId), tagIds)

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
        removeTags(Collections.singleton(bookId), tagIds)

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
     * Update comic book path
     */
    @Update(entity = ComicBook::class, onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun updatePath(newPath: NewBookPath)

    /**
     * Update comic book title
     */
    @Update(entity = ComicBook::class, onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun updateTitle(newTitle: NewBookTitle)

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

    @Query(
        """
        SELECT DISTINCT ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_FILE_PATH}
        FROM ${ComicBook.TABLE_NAME}
            LEFT JOIN ${TaggedComicBook.TABLE_NAME} ON ${TaggedComicBook.COLUMN_BOOK_ID} = ${ComicBook.COLUMN_ID}
        WHERE ${TaggedComicBook.TABLE_NAME}.${TaggedComicBook.COLUMN_TAG_ID} IN (:tagIds)
    """
    )
    abstract suspend fun pathByTag(tagIds: Set<Long>): List<Uri>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertInner(comicBook: ComicBook): Long

    @Query(
        """
        SELECT $SIMPLE_BOOK_COLUMNS,
        CASE
            WHEN ${ComicBook.COLUMN_FILE_PATH} = :path THEN ${FindResult.SQL_BY_PATH}
            ELSE ${FindResult.SQL_BY_CONTENT}
            END ${FindResult.COLUMN_FOUND_TYPE}
        FROM ${ComicBook.TABLE_NAME}
        WHERE ${ComicBook.COLUMN_FILE_PATH} = :path OR 
        (${ComicBook.COLUMN_FILE_HASH} = :fileHash AND ${ComicBook.COLUMN_FILE_SIZE} = :fileSize)
        LIMIT 1
    """
    )
    @Transaction
    protected abstract suspend fun findByPathOrContent(
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
            ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_ACTION_TIME}
        """
    }
}

