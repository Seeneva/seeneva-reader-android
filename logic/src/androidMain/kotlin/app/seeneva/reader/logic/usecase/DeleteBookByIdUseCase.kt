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

package app.seeneva.reader.logic.usecase

import app.seeneva.reader.data.source.local.db.dao.ComicBookSource
import app.seeneva.reader.logic.comic.LibraryFileManager

/**
 * Use [app.seeneva.reader.logic.comic.Library] tol call it
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