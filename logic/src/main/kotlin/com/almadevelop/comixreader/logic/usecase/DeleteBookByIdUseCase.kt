package com.almadevelop.comixreader.logic.usecase

import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookSource
import com.almadevelop.comixreader.logic.comic.LibraryFileManager

/**
 * Use [com.almadevelop.comixreader.logic.comic.Library] tol call it
 */
internal interface DeleteBookByIdUseCase {
    suspend fun delete(ids: Collection<Long>)
}

internal class DeleteBookByIdUseCaseImpl(
    private val fileManager: LibraryFileManager,
    private val comicBookSource: ComicBookSource
) : DeleteBookByIdUseCase {
    override suspend fun delete(ids: Collection<Long>) {
        if (ids.isEmpty()) {
            return
        }

        val removedPaths = comicBookSource.pathById(ids.toHashSet())

        fileManager.remove(removedPaths)
    }
}