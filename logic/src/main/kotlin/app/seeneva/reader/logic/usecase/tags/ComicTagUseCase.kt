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

package app.seeneva.reader.logic.usecase.tags

import app.seeneva.reader.data.source.local.db.LocalTransactionRunner
import app.seeneva.reader.data.source.local.db.dao.ComicBookSource

interface ComicTagUseCase {
    /**
     * Toggle comic book tag
     * @param id comic book id
     */
    suspend fun toggle(id: Long)

    suspend fun change(id: Long, add: Boolean) {
        change(setOf(id), add)
    }

    /**
     * Mark comic books with tag
     * @param ids comic book ids to mark to
     * @param add add or remove tag from comic book
     */
    suspend fun change(ids: Set<Long>, add: Boolean)
}

internal abstract class BaseComicTagUseCase(
    private val comicBookSource: ComicBookSource,
    private val localTransactionRunner: LocalTransactionRunner
) : ComicTagUseCase {
    override suspend fun toggle(id: Long) {
        localTransactionRunner.run {
            val tagId = getTagId()

            val hasTag = comicBookSource.hasTag(id, tagId) ?: return@run

            if (hasTag) {
                comicBookSource.removeTags(id, setOf(tagId))
            } else {
                comicBookSource.addTags(id, setOf(tagId))
            }
        }
    }

    override suspend fun change(ids: Set<Long>, add: Boolean) {
        if (ids.isEmpty()) {
            return
        }

        localTransactionRunner.run {
            comicBookSource.changeTags(ids, setOf(getTagId()), add)
        }
    }

    protected abstract suspend fun getTagId(): Long
}