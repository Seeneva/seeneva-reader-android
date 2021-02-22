package com.almadevelop.comixreader.logic.usecase

import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookSource
import com.almadevelop.comixreader.logic.comic.LibraryFileManager

/**
 * Use [com.almadevelop.comixreader.logic.comic.Library] tol call it
 */
internal interface DeleteBookByIdUseCase {
    suspend fun delete(ids: Set<Long>)
}

internal class DeleteBookByIdUseCaseImpl(
    private val fileManager: LibraryFileManager,
    private val comicBookSource: ComicBookSource
) : DeleteBookByIdUseCase {
    override suspend fun delete(ids: Set<Long>) {
        if (ids.isEmpty()) {
            return
        }

        val pathsToRemove = comicBookSource.pathById(ids)

        if (pathsToRemove.isNotEmpty()) {
            //delete from a database
            comicBookSource.delete(ids)
            //delete from a disk
            fileManager.remove(pathsToRemove)
        }
    }
}