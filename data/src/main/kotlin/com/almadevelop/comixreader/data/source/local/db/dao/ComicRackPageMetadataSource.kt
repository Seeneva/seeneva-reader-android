package com.almadevelop.comixreader.data.source.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.almadevelop.comixreader.data.entity.ComicRackPageMetadata

@Dao
interface ComicRackPageMetadataSource {
    /**
     * Insert or replace metadata pages
     * @param pages comic book metadata pages
     * @return list of pages ids
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(pages: List<ComicRackPageMetadata>): List<Long>
}