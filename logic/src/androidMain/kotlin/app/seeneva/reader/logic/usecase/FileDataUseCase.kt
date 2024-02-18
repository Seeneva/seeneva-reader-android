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

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.core.net.toFile
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
     */
    private fun extractFileData(paths: List<Uri>): Flow<FileData> {
        return if (paths.isEmpty()) {
            emptyFlow()
        } else {
            paths.asFlow().map(::getPathData).flowOn(dispatchers.io)
        }
    }

    /**
     * Convert provided [path] into [FileData]
     * @param path comic book path
     * @return result [FileData]
     */
    private suspend fun getPathData(path: Uri): FileData {
        val projection = arrayOf(
            // DocumentsContract.Document.COLUMN_DISPLAY_NAME == OpenableColumns.DISPLAY_NAME
            OpenableColumns.DISPLAY_NAME,
            // Some file managers doesn't return '_display_name', so we will try to fallback here
            // https://github.com/Seeneva/seeneva-reader-android/issues/25
            MediaStore.MediaColumns.TITLE,
            OpenableColumns.SIZE
        )

        return io {
            when (path.scheme) {
                ContentResolver.SCHEME_FILE ->
                    MatrixCursor(projection).also {
                        it.addRow(arrayOf(path.lastPathSegment, null, path.toFile().length()))
                    }
                ContentResolver.SCHEME_CONTENT -> {
                    context.contentResolver
                        .query(
                            path,
                            projection,
                            null,
                            null,
                            null
                        )
                }
                else -> throw RuntimeException("Unsupported comic book scheme type: $path")
            }?.use {
                if (it.moveToFirst()) {
                    val fileName =
                        it.getStringOrNull(0)
                            ?: it.getStringOrNull(1)
                            ?: throw RuntimeException("Can't get comic book file name: $path")

                    val fileSize =
                        it.getLongOrNull(2)
                            ?: throw RuntimeException("Can't get comic book file size: $path")

                    FileData(path, fileName, fileSize)
                } else {
                    null
                }
            }
        } ?: throw RuntimeException("Can't get cursor for comic book path: $path")
    }
}