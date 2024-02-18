/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.seeneva.reader.data.entity.ComicTag

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