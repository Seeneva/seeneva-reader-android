package com.almadevelop.comixreader.data.source.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.withTransaction
import com.almadevelop.comixreader.data.entity.ComicRackMetadata
import com.almadevelop.comixreader.data.source.local.db.ComicDatabase

@Dao
internal abstract class ComicRackMetadataSource internal constructor(private val database: ComicDatabase) {
    /**
     * Insert or replace comic book metadata
     * @param metadata comic book metadata to insert
     * @return metadata id
     */
    suspend fun insertOrReplace(metadata: ComicRackMetadata): Long =
        database.withTransaction {
            insertOrReplaceInner(metadata).also { metadataId ->
                if (!metadata.pages.isNullOrEmpty()) {
                    database.comicRackPageMetadataSource()
                        .insertOrReplace(metadata.pages.map { it.copy(metadataId = metadataId) })
                }
            }
        }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertOrReplaceInner(metadata: ComicRackMetadata): Long
}