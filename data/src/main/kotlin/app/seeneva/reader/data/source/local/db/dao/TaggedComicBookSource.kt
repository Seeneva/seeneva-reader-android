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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import app.seeneva.reader.data.source.local.db.entity.TaggedComicBook

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