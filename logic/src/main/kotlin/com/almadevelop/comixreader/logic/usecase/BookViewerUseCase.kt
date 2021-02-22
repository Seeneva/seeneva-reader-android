package com.almadevelop.comixreader.logic.usecase

import android.net.Uri
import com.almadevelop.comixreader.common.coroutines.Dispatched
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookSource
import com.almadevelop.comixreader.data.source.local.db.dao.ComicTagSource
import com.almadevelop.comixreader.logic.comic.Library
import com.almadevelop.comixreader.logic.comic.LibraryFileManager
import com.almadevelop.comixreader.logic.entity.ComicBookDescription
import com.almadevelop.comixreader.logic.entity.Direction
import com.almadevelop.comixreader.logic.entity.TagType
import com.almadevelop.comixreader.logic.extension.getOrCreateHardcodedTagId
import com.almadevelop.comixreader.logic.mapper.ComicBookIntoDescription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import org.tinylog.kotlin.Logger

interface BookViewerUseCase {
    /**
     * Subscribe to comic book description updates by [id]
     * @param id comic book id
     * @return emit null if there is no such comic book
     */
    fun subscribe(id: Long): Flow<ComicBookDescription?>

    /**
     * Check is provided comic book path is persisted and valid
     * @param bookId comic book id
     * @param bookPath comic book path
     */
    suspend fun checkPersisted(bookId: Long, bookPath: Uri): Boolean

    /**
     * Set provided page as comic book cover
     * @param bookId comic book id
     * @param pagePosition page position in the comic book container to set as cover
     */
    suspend fun setPageAsCover(bookId: Long, pagePosition: Long)

    /**
     * Save provided comic book page as last read position
     * @param bookId comic book id
     * @param pagePosition page position in the comic book container to save as read position
     */
    suspend fun saveReadPosition(bookId: Long, pagePosition: Long)

    /**
     * Update comic book direction
     * @param bookId comic book id
     * @param direction new comic book direction
     */
    suspend fun updateDirection(bookId: Long, direction: Direction)
}

internal class BookViewerUseCaseImpl(
    private val comicBookSource: ComicBookSource,
    private val tagSource: ComicTagSource,
    private val library: Library,
    private val fileManager: LibraryFileManager,
    private val mapper: ComicBookIntoDescription,
    override val dispatchers: Dispatchers
) : BookViewerUseCase, Dispatched {
    override fun subscribe(id: Long) =
        comicBookSource.subscribeFullById(id)
            .onStart {
                //set action time to the current time
                comicBookSource.updateActionTime(id)
            }
            .onCompletion {
                if (it != null && it !is CancellationException) {
                    Logger.error(it, "Viewer can't open comic book $id")
                }
            }
            .onEach {
                if (it == null) {
                    Logger.error("Viewer can't open comic book $id. It cannot be found")
                }
            }
            .conflate()
            .map {
                it?.let {
                    //check current file persist state
                    val persisted = checkPersisted(it.comicBook.id, it.comicBook.filePath)

                    mapper(it.comicBook, persisted)
                }
            }
            .distinctUntilChanged()
            .flowOn(dispatchers.io)


    override suspend fun checkPersisted(bookId: Long, bookPath: Uri): Boolean {
        //wait while library sync is finished
        library.state.first { it == Library.State.IDLE }

        return fileManager.isPathPersisted(bookPath).also { persisted ->
            val corruptedTagId = tagSource.getOrCreateHardcodedTagId(TagType.TYPE_CORRUPTED)

            comicBookSource.editTags {
                //if comic book is not persisted than set corrupted tag
                if (!persisted) {
                    addTags(bookId, setOf(corruptedTagId))
                } else {
                    removeTags(bookId, setOf(corruptedTagId))
                }
            }
        }
    }

    override suspend fun setPageAsCover(bookId: Long, pagePosition: Long) {
        comicBookSource.updateCoverPosition(bookId, pagePosition)
    }

    override suspend fun saveReadPosition(bookId: Long, pagePosition: Long) {
        comicBookSource.updateReadPosition(bookId, pagePosition)
    }

    override suspend fun updateDirection(bookId: Long, direction: Direction) {
        comicBookSource.updateDirection(bookId, direction.id)
    }
}