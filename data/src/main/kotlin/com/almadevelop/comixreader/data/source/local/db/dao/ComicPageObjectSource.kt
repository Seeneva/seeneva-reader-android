package com.almadevelop.comixreader.data.source.local.db.dao

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.almadevelop.comixreader.data.entity.ComicPageObject
import com.almadevelop.comixreader.data.source.local.db.entity.ComicPageObjectText

@Dao
abstract class ComicPageObjectSource {
    /**
     * Insert or replace comic book page's objects
     * @param objects objects to insert
     * @return objects ids
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrReplace(objects: List<ComicPageObject>): List<Long>

    /**
     * Select comic book page ML objects
     *
     * @param pageId parent page id
     * @param classIds optional set of requited objects classes. Pass empty to retrieve all objects on the page
     */
    suspend fun getObjects(pageId: Long, classIds: Set<Long> = emptySet()): List<ComicPageObject> {
        val args = mutableListOf<Any>()

        val query = buildString {
            appendLine("SELECT * FROM ${ComicPageObject.TABLE_NAME} WHERE ${ComicPageObject.COLUMN_PAGE_ID} == ?")

            args += pageId

            if (classIds.isNotEmpty()) {
                appendLine("AND ${ComicPageObject.COLUMN_CLASS_ID} IN(${classIds.joinToString { "?" }})")

                args.addAll(classIds)
            }
        }

        return getObjectsInner(SimpleSQLiteQuery(query, args.toTypedArray()))
    }

    /**
     * Get saved recognized text for comic book page object if any
     * @param id comic book page object id
     * @param language text language code
     * @return object's text or null if there is no saved text
     */
    @Query(
        """
        SELECT ${ComicPageObjectText.COLUMN_TEXT} 
        FROM ${ComicPageObjectText.TABLE_NAME} 
        WHERE ${ComicPageObjectText.COLUMN_OBJECT_ID} == :id AND ${ComicPageObjectText.COLUMN_LANGUAGE} == :language
        LIMIT 1
        """
    )
    abstract suspend fun getRecognizedText(id: Long, language: String): String?

    /**
     * Set recognized text for comic book page object
     * @param id comic book page object id
     * @param language text language code
     * @param text text to save. Can be empty
     * @return text id
     */
    suspend fun setRecognizedText(id: Long, language: String, text: String): Long =
        setObjectTextInner(ComicPageObjectText(objectId = id, language = language, text = text))

    /**
     * Select comic book page ML object
     *
     * @param query SQLite query
     */
    @RawQuery
    protected abstract suspend fun getObjectsInner(query: SupportSQLiteQuery): List<ComicPageObject>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun setObjectTextInner(objextText: ComicPageObjectText): Long
}