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

package app.seeneva.reader.logic.usecase

import android.content.Context
import app.seeneva.reader.common.coroutines.Dispatched
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.data.source.local.db.dao.ComicBookSource
import app.seeneva.reader.data.source.local.db.dao.ComicTagSource
import app.seeneva.reader.logic.comic.LibraryFileManager
import app.seeneva.reader.logic.entity.TagType
import app.seeneva.reader.logic.entity.query.QueryParams
import app.seeneva.reader.logic.entity.query.QueryParamsResolver
import app.seeneva.reader.logic.entity.query.addDefaultFilters
import app.seeneva.reader.logic.extension.getHardcodedTagId
import app.seeneva.reader.logic.extension.getOrCreateHardcodedTag
import app.seeneva.reader.logic.extension.hasTag
import org.tinylog.kotlin.Logger
import java.util.*

/**
 * Use [app.seeneva.reader.logic.comic.Library] tol call it
 */
internal interface SyncUseCase {
    suspend fun start()
}

internal class SyncUseCaseImpl(
    context: Context,
    private val fileManager: LibraryFileManager,
    private val queryParamsResolver: QueryParamsResolver,
    private val comicBookSource: ComicBookSource,
    private val tagSource: ComicTagSource,
    private val deleteByIdUseCase: DeleteBookByIdUseCase,
    override val dispatchers: Dispatchers
) : SyncUseCase, Dispatched {
    private val context = context.applicationContext

    override suspend fun start() {
        Logger.debug("Sync started")

        deleteRemoved()

        val persisitedPaths = fileManager.getValidPersistedPaths()

        queryParamsResolver.resolve(QueryParams.build()) { addDefaultFilters() }
            .let { comicBookSource.querySimpleWithTags(it) }
            .also { comicBooks ->
                val corruptedTag =
                    tagSource.getOrCreateHardcodedTag(context, TagType.TYPE_CORRUPTED)

                comicBooks.forEach { comicBook ->
                    comicBookSource.editTags {
                        //if comic has permission
                        if (persisitedPaths.remove(comicBook.filePath)) {
                            if (comicBook.hasTag(corruptedTag.id)) {
                                removeTags(
                                    comicBook.id,
                                    Collections.singleton(corruptedTag.id)
                                )
                                Logger.debug("${comicBook.filePath} marked as not '${corruptedTag.name}'")
                            }
                        } else {
                            if (!comicBook.hasTag(corruptedTag.id)) {
                                addTags(
                                    comicBook.id,
                                    Collections.singleton(corruptedTag.id)
                                )
                                Logger.debug("${comicBook.filePath} marked as '${corruptedTag.name}'")
                            }
                        }
                    }
                }
            }
        //if permissions is not empty. It is dead permissions. There is no info about them in the DB
        if (persisitedPaths.isNotEmpty()) {
            fileManager.remove(persisitedPaths)
        }
    }

    /**
     * Permanent delete any marked as removed comic books
     */
    private suspend fun deleteRemoved() {
        val removeTagId = tagSource.getHardcodedTagId(TagType.TYPE_REMOVED)

        if (removeTagId != null) {
            deleteByIdUseCase.delete(comicBookSource.idByTag(setOf(removeTagId)).toHashSet())

            Logger.debug("Comic books with removed tag was deleted permanently")
        }
    }
}