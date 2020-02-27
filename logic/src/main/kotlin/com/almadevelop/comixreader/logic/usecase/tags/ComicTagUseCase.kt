package com.almadevelop.comixreader.logic.usecase.tags

import com.almadevelop.comixreader.data.source.local.db.LocalTransactionRunner
import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookSource
import java.util.*

interface ComicTagUseCase {
    /**
     * Toggle comic book tag
     * @param id comic book id
     */
    suspend fun toggle(id: Long)

    suspend fun change(id: Long, add: Boolean) {
        change(Collections.singleton(id), add)
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
                comicBookSource.removeTags(id, Collections.singleton(tagId))
            } else {
                comicBookSource.addTags(id, Collections.singleton(tagId))
            }
        }
    }

    override suspend fun change(ids: Set<Long>, add: Boolean) {
        if (ids.isEmpty()) {
            return
        }

        localTransactionRunner.run {
            comicBookSource.changeTags(ids, Collections.singleton(getTagId()), add)
        }
    }

    protected abstract suspend fun getTagId(): Long
}