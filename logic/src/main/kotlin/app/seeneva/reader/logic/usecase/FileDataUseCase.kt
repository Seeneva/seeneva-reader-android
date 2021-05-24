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

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.database.MergeCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import app.seeneva.reader.common.coroutines.Dispatched
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.common.coroutines.io
import app.seeneva.reader.common.entity.FileHashData
import app.seeneva.reader.data.source.jni.NativeSource
import app.seeneva.reader.logic.entity.FileData
import kotlinx.coroutines.flow.*

/**
 * Get file data by [Uri]
 */
interface FileDataUseCase {
    /**
     * Get a file data
     * @param path path to a file
     * @return file data
     */
    suspend fun getFileData(path: Uri): FileData

    suspend fun getFileHashData(path: Uri): FileHashData
}

internal class FileDataUseCaseImpl(
    context: Context,
    private val nativeSource: NativeSource,
    override val dispatchers: Dispatchers
) : FileDataUseCase, Dispatched {
    private val context = context.applicationContext

    override suspend fun getFileData(path: Uri): FileData {
        //data extracted from Android OS (like contentProvider)
        return io {
            extractFileData(path)
        }
    }

    override suspend fun getFileHashData(path: Uri): FileHashData {
        return io {
            //calculate comic archive hash
            nativeSource.getComicFileData(path).let {
                FileHashData(it.hash, it.size)
            }
        }
    }

    private suspend fun extractFileData(path: Uri): FileData {
        return extractFileData(listOf(path)).first()
    }

    /**
     * @param paths comic books file paths which should be used
     * @return a flow over all [paths] files data
     * @see pathsIntoCursor
     */
    private fun extractFileData(paths: List<Uri>): Flow<FileData> {
        if (paths.isEmpty()) {
            return emptyFlow()
        }

        return flow {
            pathsIntoCursor(paths).use { cursor ->
                require(cursor.count == paths.size) { "Not equal count of input and output when build cursor. In ${paths.size}, out ${cursor.count}" }

                val inputIterator = paths.iterator()

                while (cursor.moveToNext()) {
                    val path = inputIterator.next()

                    val fileName =
                        requireNotNull(cursor.getStringOrNull(0)) { "Can't get comic book file name: $path" }

                    val fileSize =
                        requireNotNull(cursor.getLongOrNull(1)) { "Can't get comic book file size: $path" }

                    emit(FileData(path, fileName, fileSize))
                }
            }
        }.flowOn(dispatchers.io)
    }

    /**
     * Convert provided [paths] into [Cursor]. Cursor's row position is correspondent to [paths] position
     * @param paths
     * @return result cursor
     */
    private suspend fun pathsIntoCursor(paths: List<Uri>): Cursor {
        require(paths.isNotEmpty()) { "Can't convert paths into Cursor. Empty comic book paths." }

        val projection = arrayOf(
            // DocumentsContract.Document.COLUMN_DISPLAY_NAME == OpenableColumns.DISPLAY_NAME
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE
        )

        val getContentCursor: suspend (Uri) -> Cursor = { path ->
            io {
                requireNotNull(
                    context.contentResolver.query(
                        path,
                        projection,
                        null,
                        null,
                        null
                    )
                ) { "Can't get cursor for comic book path: $path" }
            }
        }

        val intoCursor: suspend (Uri) -> Cursor = { path ->
            if (DocumentFile.isDocumentUri(context, path)) {
                getContentCursor(path)
            } else {
                when (path.scheme) {
                    ContentResolver.SCHEME_CONTENT -> getContentCursor(path)
                    ContentResolver.SCHEME_FILE -> {
                        io {
                            MatrixCursor(projection).also {
                                it.addRow(arrayOf(path.lastPathSegment, path.toFile().length()))
                            }
                        }
                    }
                    else -> throw Error("Unsupported comic book scheme type: $path")
                }
            }
        }

        return when (val count = paths.size) {
            1 -> intoCursor(paths.first())
            else -> MergeCursor(Array(count) { intoCursor(paths[it]) })
        }
    }
}