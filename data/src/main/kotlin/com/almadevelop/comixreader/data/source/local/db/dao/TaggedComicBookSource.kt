package com.almadevelop.comixreader.data.source.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.almadevelop.comixreader.data.source.local.db.entity.TaggedComicBook

@Dao
internal interface TaggedComicBookSource {
    /**
     * Remove tags from a comic book
     * @return number of removed comic books
     */
    @Delete
    suspend fun removeTags(taggedComicBook: List<TaggedComicBook>): Int

    /**
     * Add comic book => tag
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(taggedComicBook: List<TaggedComicBook>): List<Long>
}