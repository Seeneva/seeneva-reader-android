package com.almadevelop.comixreader.logic.usecase

import android.content.Context
import com.almadevelop.comixreader.common.coroutines.Dispatched
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookSource
import com.almadevelop.comixreader.data.source.local.db.dao.ComicTagSource
import com.almadevelop.comixreader.logic.comic.LibraryFileManager
import com.almadevelop.comixreader.logic.entity.TagType
import com.almadevelop.comixreader.logic.entity.query.QueryParams
import com.almadevelop.comixreader.logic.entity.query.QueryParamsResolver
import com.almadevelop.comixreader.logic.entity.query.addDefaultFilters
import com.almadevelop.comixreader.logic.extension.getHardcodedTagId
import com.almadevelop.comixreader.logic.extension.getOrCreateHardcodedTag
import com.almadevelop.comixreader.logic.extension.hasTag
import org.tinylog.kotlin.Logger
import java.util.*

/**
 * Use [com.almadevelop.comixreader.logic.comic.Library] tol call it
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