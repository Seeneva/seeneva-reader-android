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

import app.seeneva.reader.common.coroutines.Dispatched
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.common.coroutines.io
import app.seeneva.reader.data.NativeException
import app.seeneva.reader.data.entity.FindResult
import app.seeneva.reader.data.entity.ml.Interpreter
import app.seeneva.reader.data.source.jni.NativeSource
import app.seeneva.reader.data.source.local.db.LocalTransactionRunner
import app.seeneva.reader.data.source.local.db.dao.ComicBookSource
import app.seeneva.reader.data.source.local.db.dao.ComicTagSource
import app.seeneva.reader.data.source.local.db.entity.SimpleComicBookWithTags
import app.seeneva.reader.logic.comic.AddComicBookMode
import app.seeneva.reader.logic.comic.LibraryFileManager
import app.seeneva.reader.logic.entity.*
import app.seeneva.reader.logic.extension.getHardcodedTagId
import app.seeneva.reader.logic.extension.hasHardcodedTag
import app.seeneva.reader.logic.storage.InterpreterObjectStorage
import app.seeneva.reader.logic.storage.withBorrow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger

/**
 * Use [app.seeneva.reader.logic.comic.Library] tol call it
 */
internal interface AddingUseCase {
    suspend fun add(
        fileData: FullFileData,
        addMode: AddComicBookMode,
    ): ComicAddResult
}

internal class AddingUseCaseImpl(
    private val interpreterObjectStorage: InterpreterObjectStorage,
    private val fileManager: LibraryFileManager,
    private val nativeSource: NativeSource,
    private val comicBookSource: ComicBookSource,
    private val tagSource: ComicTagSource,
    private val localTransactionRunner: LocalTransactionRunner,
    override val dispatchers: Dispatchers
) : AddingUseCase, Dispatched {
    // can be received from app setting in the future
    private val direction
        get() = Direction.LTR

    override suspend fun add(
        fileData: FullFileData,
        addMode: AddComicBookMode,
    ): ComicAddResult {
        Logger.debug("Add comic into library by uri: '${fileData.path}'")

        /*
        1. If comic book can't be found by hash or/and path - Add new comic book
        2. Otherwise. If previously added comic book has same hash as a new one and is not marked as removed.
        2.1 If it's checked as Broken - we can fix it with a new comic book
        2.2 Otherwise we've finished.
        3. Otherwise replace an old comic book with new one.
        4. RROFIT!!!!!!!!
        */
        return when (val findResult =
            comicBookSource.findByContentOrPath(fileData.path, fileData.asFileHashData())) {
            null -> {
                //just add a new comic book without replace
                processAdding(fileData) {
                    interpreterObjectStorage.withBorrow {
                        simpleAdding(fileData, addMode, it)
                    }
                }
            }
            else -> {
                val (findType, savedComicBook) = findResult

                //check files hash
                val isFileEqual = findType == FindResult.Type.Content
                //check is current saved book removed
                val isRemoved = savedComicBook.hasHardcodedTag(TagType.TYPE_REMOVED)

                //continue adding only if it is unique file
                if (isFileEqual && !isRemoved) {
                    if (savedComicBook.hasHardcodedTag(TagType.TYPE_CORRUPTED)) {
                        processAdding(fileData) { fix(savedComicBook, fileData, addMode) }
                    } else {
                        //if content the same and it is not removed we're done
                        ComicAddResult(
                            ComicAddResult.Type.AlreadyOpened,
                            fileData
                        )
                    }
                } else {
                    //otherwise we should replace old comic book with a new one
                    processAdding(fileData) {
                        interpreterObjectStorage.withBorrow {
                            replace(
                                savedComicBook,
                                fileData,
                                addMode,
                                it
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun processAdding(
        fileData: FullFileData,
        body: suspend () -> Unit
    ): ComicAddResult {
        return kotlin.runCatching {
            body()
        }.map {
            ComicAddResult.Type.Success
        }.recoverCatching {
            if (it is NativeException) {
                return@recoverCatching when (it.code) {
                    NativeException.CODE_CONTAINER_READ ->
                        ComicAddResult.Type.ContainerReadError
                    NativeException.CODE_CONTAINER_OPEN_UNSUPPORTED ->
                        ComicAddResult.Type.ContainerUnsupportedError
                    NativeException.CODE_EMPTY_BOOK ->
                        ComicAddResult.Type.NoComicPagesError
                    else -> throw it
                }
            }

            throw it
        }.map {
            ComicAddResult(it, fileData)
        }.getOrThrow()
    }

    /**
     * Simple adding
     * @throws NativeException
     * @throws app.seeneva.reader.data.NativeFatalError
     */
    private suspend fun simpleAdding(
        fileData: SimpleFileData,
        addMode: AddComicBookMode,
        interpreter: Interpreter
    ) {
        io {
            val comicBookPath = fileManager.add(fileData, addMode)

            try {
                val comicsMetadata =
                    nativeSource.getComicsMetadata(
                        comicBookPath,
                        fileData.nameWithoutExtension,
                        direction.id,
                        interpreter
                    )

                ensureActive()

                comicBookSource.insertOrReplace(comicsMetadata)
            } catch (t: Throwable) {
                //use noncancellable context to prevent cancel remove tmp files
                withContext(NonCancellable) {
                    fileManager.remove(comicBookPath)
                }
                //rethrow exception
                throw t
            }
        }
    }

    /**
     * Replace previously added comic book
     */
    private suspend fun replace(
        toReplace: SimpleComicBookWithTags,
        fileData: SimpleFileData,
        addMode: AddComicBookMode,
        interpreter: Interpreter
    ) {
        io {
            val comicBookPath = fileManager.replace(toReplace.filePath, fileData, addMode)

            try {
                val comicsMetadata =
                    nativeSource.getComicsMetadata(
                        comicBookPath,
                        fileData.nameWithoutExtension,
                        direction.id,
                        interpreter
                    ).copy(id = toReplace.id)

                ensureActive()

                comicBookSource.insertOrReplace(comicsMetadata)
            } catch (t: Throwable) {
                //use noncancellable context to prevent cancel remove tmp files
                withContext(NonCancellable) {
                    fileManager.remove(comicBookPath)
                }
                throw t
            }
        }
    }

    /**
     * Fix corrupted comic book by updating it path
     */
    private suspend fun fix(
        fix: SimpleComicBookWithTags,
        fileData: SimpleFileData,
        addMode: AddComicBookMode
    ) {
        io {
            val fixedPath = fileManager.replace(fix.filePath, fileData, addMode)

            try {
                val corruptedId =
                    requireNotNull(tagSource.getHardcodedTagId(TagType.TYPE_CORRUPTED)) { "Corrupted tag cannot be null" }

                coroutineContext.ensureActive()

                localTransactionRunner.run {
                    comicBookSource.removeTags(fix.id, setOf(corruptedId))

                    comicBookSource.updatePath(fix.id, fixedPath)
                }
            } catch (t: Throwable) {
                //use noncancellable context to prevent cancel remove tmp files
                withContext(NonCancellable) {
                    fileManager.remove(fixedPath)
                }
                throw t
            }
        }
    }
}