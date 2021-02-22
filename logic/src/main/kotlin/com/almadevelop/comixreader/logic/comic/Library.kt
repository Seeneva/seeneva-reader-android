package com.almadevelop.comixreader.logic.comic

import com.almadevelop.comixreader.logic.entity.ComicAddResult
import com.almadevelop.comixreader.logic.entity.FullFileData
import com.almadevelop.comixreader.logic.usecase.AddingUseCase
import com.almadevelop.comixreader.logic.usecase.DeleteBookByIdUseCase
import com.almadevelop.comixreader.logic.usecase.SyncUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.yield
import org.tinylog.kotlin.Logger
import kotlin.coroutines.coroutineContext

interface Library {
    /**
     * Flow of library states
     */
    val state: StateFlow<State>

    /**
     * Add comic book into user library by provided [fileData]
     *
     * @param fileData comic book data
     * @param addMode adding mode
     * @return comic book metadata result
     */
    suspend fun add(
        fileData: FullFileData,
        addMode: AddComicBookMode,
    ): ComicAddResult

    /**
     * Permanent delete comic book by provided [id]
     * @param id comic book id to delete
     */
    suspend fun delete(id: Long) {
        delete(setOf(id))
    }

    /**
     * Permanent delete comic books by provided [ids]
     * @param ids comic books ids to delete
     */
    suspend fun delete(ids: Set<Long>)

    /**
     * Sync all app's persisted permissions and files. Compare it to actual state.
     */
    suspend fun sync()

    /**
     * State of the library
     */
    enum class State {
        /**
         * Adding or removing operation is in progress
         */
        CHANGING,

        /**
         * Syncing operation is in progress
         */
        SYNCING,

        /**
         * Library is idle
         */
        IDLE
    }
}

internal class LibraryImpl(
    lazyAddingUseCase: Lazy<AddingUseCase>,
    lazySyncUseCase: Lazy<SyncUseCase>,
    lazyDeleteBookByIdUseCase: Lazy<DeleteBookByIdUseCase>
) : Library {
    private val addingUseCase by lazyAddingUseCase
    private val syncUseCase by lazySyncUseCase
    private val deleteBookByIdUseCase by lazyDeleteBookByIdUseCase

    private val mutex = Mutex()

    override val state = MutableStateFlow(Library.State.IDLE)

    override suspend fun add(fileData: FullFileData, addMode: AddComicBookMode): ComicAddResult {
        Logger.debug("Add comic into adding queue by uri: '${fileData.path}'")

        return mutex.withStateLock(Library.State.CHANGING) {
            addingUseCase.add(fileData, addMode)
        }
    }

    override suspend fun delete(ids: Set<Long>) {
        if (ids.isEmpty()) {
            return
        }

        mutex.withStateLock(Library.State.CHANGING) {
            deleteBookByIdUseCase.delete(ids)
        }
    }

    override suspend fun sync() {
        //can be called only by one coroutine at single moment
        if (mutex.tryLock()) {
            Logger.debug("Start sync library")

            try {
                state.value = Library.State.SYNCING

                syncUseCase.start()
            } finally {
                state.value = Library.State.IDLE
                mutex.unlock()
                Logger.debug("Sync finished")
            }
        } else {
            while (coroutineContext.isActive && mutex.isLocked) {
                yield()
            }
        }
    }

    private suspend inline fun <T> Mutex.withStateLock(state: Library.State, action: () -> T): T {
        lock()
        try {
            this@LibraryImpl.state.value = state
            return action()
        } finally {
            this@LibraryImpl.state.value = Library.State.IDLE
            unlock()
        }
    }
}