package com.almadevelop.comixreader.data.source.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.almadevelop.comixreader.data.entity.ComicTag

@Dao
interface ComicTagSource {
    /**
     * Try to find comic book tag by it [type]
     * @param type comic book tag type
     * @return comic book tag if it exists
     */
    @Query(
        """
        SELECT * FROM ${ComicTag.TABLE_NAME}
        WHERE ${ComicTag.COLUMN_TYPE} = :type
        LIMIT 1
    """
    )
    suspend fun findByType(type: Int): ComicTag?

    /**
     * Add or replace existed comic book tags
     * @param tags tags to insert or replace
     * @return inserted tags ids
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(vararg tags: ComicTag): List<Long>
}