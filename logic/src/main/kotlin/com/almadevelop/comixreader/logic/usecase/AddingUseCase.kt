package com.almadevelop.comixreader.logic.usecase

import com.almadevelop.comixreader.common.coroutines.Dispatched
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.common.coroutines.default
import com.almadevelop.comixreader.data.NativeException
import com.almadevelop.comixreader.data.entity.FindResult
import com.almadevelop.comixreader.data.source.jni.NativeSource
import com.almadevelop.comixreader.data.source.local.db.LocalTransactionRunner
import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookSource
import com.almadevelop.comixreader.data.source.local.db.dao.ComicTagSource
import com.almadevelop.comixreader.data.source.local.db.entity.NewBookPath
import com.almadevelop.comixreader.data.source.local.db.entity.SimpleComicBookWithTags
import com.almadevelop.comixreader.logic.comic.AddComicBookMode
import com.almadevelop.comixreader.logic.comic.LibraryFileManager
import com.almadevelop.comixreader.logic.entity.*
import com.almadevelop.comixreader.logic.extension.getHardcodedTagId
import com.almadevelop.comixreader.logic.extension.hasHardcodedTag
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger
import java.util.*
import kotlin.coroutines.coroutineContext

/**
 * Use [com.almadevelop.comixreader.logic.comic.Library] tol call it
 */
internal interface AddingUseCase {
    suspend fun add(fileData: FullFileData, addMode: AddComicBookMode): ComicAddResult
}

internal class AddingUseCaseImpl(
    private val fileManager: LibraryFileManager,
    private val nativeSource: NativeSource,
    private val comicBookSource: ComicBookSource,
    private val tagSource: ComicTagSource,
    private val localTransactionRunner: LocalTransactionRunner,
    override val dispatchers: Dispatchers
) : AddingUseCase, Dispatched {
    override suspend fun add(fileData: FullFileData, addMode: AddComicBookMode): ComicAddResult {
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
            comicBookSource.findByPathOrContent(fileData.path, fileData.asFileHashData())) {
            null -> {
                //just add a new comic book without replace
                processAdding(fileData) { simpleAdding(fileData, addMode) }
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
                    processAdding(fileData) { replace(savedComicBook, fileData, addMode) }
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
                    NativeException.CODE_CONTAINER_OPEN_MAGIC_IO ->
                        ComicAddResult.Type.ContainerMagicIOError
                    NativeException.CODE_CONTAINER_OPEN_UNKNOWN_FORMAT ->
                        ComicAddResult.Type.ContainerUnknownFileFormatError
                    NativeException.CODE_EMPTY_BOOK ->
                        ComicAddResult.Type.NoComicPagesError
                    NativeException.CODE_IMAGE_OPEN ->
                        ComicAddResult.Type.CantOpenPageImage
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
     */
    private suspend fun simpleAdding(fileData: SimpleFileData, addMode: AddComicBookMode) {
        default {
            val comicBookPath = fileManager.add(fileData, addMode)

            try {
                val comicsMetadata =
                    nativeSource.getComicsMetadata(comicBookPath, fileData.nameWithoutExtension)

                ensureActive()

                comicBookSource.insertOrReplace(comicsMetadata)
            } catch (_: Throwable) {
                //use noncancellable context to prevent cancel remove tmp files
                withContext(NonCancellable) {
                    fileManager.remove(comicBookPath)
                }
            }
        }
    }

    private suspend fun replace(
        toReplace: SimpleComicBookWithTags,
        fileData: SimpleFileData,
        addMode: AddComicBookMode
    ) {
        default {
            val comicBookPath = fileManager.replace(toReplace.filePath, fileData, addMode)

            try {
                val comicsMetadata =
                    nativeSource.getComicsMetadata(comicBookPath, fileData.nameWithoutExtension)
                        .copy(id = toReplace.id)

                ensureActive()

                comicBookSource.insertOrReplace(comicsMetadata)
            } catch (_: Throwable) {
                //use noncancellable context to prevent cancel remove tmp files
                withContext(NonCancellable) {
                    fileManager.remove(comicBookPath)
                }
            }
        }
    }

    private suspend fun fix(
        fix: SimpleComicBookWithTags,
        fileData: SimpleFileData,
        addMode: AddComicBookMode
    ) {
        val fixedPath = fileManager.replace(fix.filePath, fileData, addMode)

        try {
            val corruptedId =
                requireNotNull(tagSource.getHardcodedTagId(TagType.TYPE_CORRUPTED)) { "Corrupted tag cannot be null" }

            coroutineContext.ensureActive()

            localTransactionRunner.run {
                comicBookSource.removeTags(fix.id, Collections.singleton(corruptedId))

                comicBookSource.updatePath(NewBookPath(fix.id, fixedPath))
            }
        } catch (_: Throwable) {
            //use noncancellable context to prevent cancel remove tmp files
            withContext(NonCancellable) {
                fileManager.remove(fixedPath)
            }
        }

    }
}