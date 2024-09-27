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

package app.seeneva.reader.logic.comic

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.seeneva.reader.common.coroutines.Dispatched
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.common.coroutines.io
import app.seeneva.reader.logic.entity.SimpleFileData
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tinylog.kotlin.Logger
import kotlin.coroutines.coroutineContext

internal interface LibraryFileManager {
    /**
     * Unlink and/or remove related comic book file
     *
     * @param path path to comic book which should be removed
     */
    suspend fun remove(path: Uri) {
        remove(listOf(path))
    }

    /**
     * Unlink and/or remove related comic book files
     *
     * @param paths path to comic books which should be removed
     */
    suspend fun remove(paths: Collection<Uri>)

    /**
     * Generate persist uri for provided [fileData] and/or save file
     *
     * @return return a result persisted comic book path. Can different from [fileData] path
     */
    suspend fun add(fileData: SimpleFileData, addMode: AddComicBookMode): Uri

    /**
     * Replace old path with a new one
     * @param oldPath old comic's path to replace
     * @param newFileData new comic book file data
     * @param addMode mode of adding
     * @return return a result persisted comic book path. Can different from [newFileData] path
     */
    suspend fun replace(oldPath: Uri, newFileData: SimpleFileData, addMode: AddComicBookMode): Uri

    /**
     * Return all valid comic book path
     */
    suspend fun getValidPersistedPaths(): MutableSet<Uri>

    /**
     * Check is provided comic book path valid or not
     * @param path comic book path to check
     * @return true if comic book valid
     */
    suspend fun isPathPersisted(path: Uri): Boolean
}

/**
 * Helper to prevent race between adding and removing comic book files
 */
internal class LibraryFileManagerImpl(
    context: Context,
    override val dispatchers: Dispatchers
) : LibraryFileManager, Dispatched {
    private val mutex = Mutex()

    private val context = context.applicationContext

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    override suspend fun remove(paths: Collection<Uri>) {
        mutex.withLock { removeInner(paths) }
    }

    override suspend fun add(fileData: SimpleFileData, addMode: AddComicBookMode): Uri =
        mutex.withLock { addInner(fileData, addMode) }

    override suspend fun replace(
        oldPath: Uri,
        newFileData: SimpleFileData,
        addMode: AddComicBookMode
    ): Uri =
        mutex.withLock {
            Logger.debug("Replace library path $oldPath with ${newFileData.path}")

            removeInner(listOf(oldPath))

            addInner(newFileData, addMode)
        }

    override suspend fun getValidPersistedPaths(): MutableSet<Uri> {
        val paths = hashSetOf<Uri>()

        io {

            allLinkedComicBooks().collect {
                ensureActive()
                paths += it
            }

            //get all files inside user library folder and convert them into Uri
            allAddedComicBooks().forEach {
                ensureActive()
                paths += it
            }
        }

        return paths
    }

    override suspend fun isPathPersisted(path: Uri) =
        when (val scheme = path.scheme) {
            ContentResolver.SCHEME_CONTENT -> allLinkedComicBooks()
            ContentResolver.SCHEME_FILE -> allAddedComicBooks().asFlow()
            else -> throw IllegalArgumentException("Unsupported comic book path scheme: $scheme")
        }.firstOrNull { it == path }?.let { true } ?: false

    /**
     * @return all current linked comic books (using [android.provider.DocumentsContract.Document])
     */
    private fun allLinkedComicBooks(): Flow<Uri> =
        context.contentResolver
            .persistedUriPermissions
            .asFlow()
            .filter {
                val isDocumentExists =
                    requireNotNull(DocumentFile.fromSingleUri(context, it.uri)).exists()

                if (!isDocumentExists) {
                    //remove invalid persisted document
                    changePersistPermissions(false, it.uri)
                }

                isDocumentExists && it.isReadPermission
            }.map { it.uri }

    /**
     * @return all currently added comic books (using file system)
     */
    private fun allAddedComicBooks(): Sequence<Uri> =
        ComicHelper.innerComicBookLibraryDir(context)
            .walkTopDown()
            .maxDepth(1)
            .filterNot { it.isDirectory }
            .map { it.toUri() }

    private suspend fun removeInner(paths: Collection<Uri>) {
        if (paths.isEmpty()) {
            return
        }

        paths.forEach {
            coroutineContext.ensureActive()

            Logger.debug("Remove library path $it")

            unlinkOrDeleteComicBook(it)
        }
    }

    private suspend fun addInner(fileData: SimpleFileData, addMode: AddComicBookMode): Uri {
        Logger.debug("Add library path ${fileData.path}")

        return linkOrAddComicBook(fileData, addMode)
    }

    /**
     * Unlink or remove previously added comic book
     * @param comicBookPath path to the previously added comic book
     * @return true if comic book was unlinked or removed
     * @throws IllegalArgumentException if provided [comicBookPath] can't be used
     */
    private suspend fun unlinkOrDeleteComicBook(comicBookPath: Uri): Boolean {
        return if (DocumentFile.isDocumentUri(context, comicBookPath)) {
            //it is document. We should unlink it
            changePersistPermissions(false, comicBookPath)
            true
        } else {
            if (comicBookPath.scheme == ContentResolver.SCHEME_FILE &&
                comicBookPath.path?.startsWith(ComicHelper.innerComicBookLibraryDir(context).path) == true
            ) {
                io { comicBookPath.toFile().delete() }
            } else {
                throw IllegalArgumentException("Can't delete provided uri. It is not related to the app comic library. $comicBookPath")
            }
        }
    }

    private suspend fun linkOrAddComicBook(
        fileData: SimpleFileData,
        addMode: AddComicBookMode
    ): Uri {
        return when (addMode) {
            AddComicBookMode.Import -> {
                //we should copy comic book into app directory

                //calculate comic book path. Rename target file name if it is already exists in the library
                val calculateTargetPath = suspend {
                    io {
                        val libraryDir = ComicHelper.innerComicBookLibraryDir(context)

                        var targetPath = libraryDir.resolve(fileData.name)

                        if (targetPath.exists()) {
                            val baseName = targetPath.nameWithoutExtension
                            val extension = targetPath.extension

                            var i = 0

                            do {
                                targetPath = libraryDir.resolve("${baseName}_(${i++}).$extension")
                            } while (targetPath.exists())

                        }

                        targetPath
                    }
                }

                val targetPath = calculateTargetPath()

                //copy from [comicBookData.path] into [targetPath]
                io {
                    targetPath.outputStream().use { out ->
                        val tis = contentResolver.openInputStream(fileData.path)

                        requireNotNull(tis) { "Can't generatePersistUri input stream from a provided uri" }
                            .use { fin -> fin.copyTo(out) }
                    }
                }

                targetPath.toUri()
            }

            AddComicBookMode.Link -> {
                //it is document. We can link it
                val success = io {
                    if (DocumentFile.isDocumentUri(context, fileData.path)) {
                        runCatching { changePersistPermissions(true, fileData.path) }
                            .map { true }
                            .getOrDefault(false)
                    } else {
                        false
                    }
                }

                if (success) {
                    fileData.path
                } else {
                    //in any case just import a file. It is more user friendly IMHO
                    linkOrAddComicBook(fileData, AddComicBookMode.Import)
                }
            }
        }
    }

    /**
     * Change persisted permissions from provided [comicBookPaths].
     * @param grant grant or remove permission
     * @param comicBookPaths uris which permissions should be changed
     *
     * @see ContentResolver.releasePersistableUriPermission
     */
    private suspend fun changePersistPermissions(grant: Boolean, vararg comicBookPaths: Uri) {
        if (comicBookPaths.isEmpty()) {
            return
        }

        io {
            if (grant) {
                comicBookPaths.forEach {
                    ensureActive()

                    contentResolver.takeComicPermission(it)
                }
            } else {
                val persistedPermissions = contentResolver.persistedUriPermissions

                comicBookPaths.forEach { releasingUri ->
                    ensureActive()

                    //try to find permission for provided uri
                    //if we still has permission let's release it
                    if (persistedPermissions.find { it.uri == releasingUri } != null) {
                        contentResolver.releaseComicPermission(releasingUri)
                    }
                }
            }
        }
    }
}