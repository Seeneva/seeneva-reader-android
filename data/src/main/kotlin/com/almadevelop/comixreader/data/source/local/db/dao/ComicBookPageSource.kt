package com.almadevelop.comixreader.data.source.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.almadevelop.comixreader.data.entity.ComicBookPage

@Dao
interface ComicBookPageSource {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pages: List<ComicBookPage>): List<Long>
}