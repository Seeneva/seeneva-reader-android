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

package app.seeneva.reader.screen.viewer

import android.net.Uri
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.logic.entity.ComicBookDescription
import app.seeneva.reader.logic.entity.Direction
import app.seeneva.reader.logic.entity.configuration.ViewerConfig
import app.seeneva.reader.logic.usecase.BookViewerUseCase
import app.seeneva.reader.logic.usecase.ViewerConfigUseCase
import app.seeneva.reader.viewmodel.CoroutineViewModel
import app.seeneva.reader.viewmodel.EventSender
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.tinylog.kotlin.Logger

sealed interface BookDescriptionState {
    /**
     * Loading is finished
     * @param description comic book description
     */
    data class Loaded(val description: ComicBookDescription) : BookDescriptionState
    data class Error(val error: Throwable) : BookDescriptionState
    data class Loading(val id: Long) : BookDescriptionState
    object NotFound : BookDescriptionState
    object Corrupted : BookDescriptionState
    object Idle : BookDescriptionState
}

sealed interface ViewerConfigState {
    object Loading : ViewerConfigState
    data class Loaded(val config: ViewerConfig) : ViewerConfigState
}

sealed interface BookDescriptionEvent {
    /**
     * Comic book cover was changed
     */
    data class CoverChanged(val position: Int) : BookDescriptionEvent
}

interface BookViewerViewModel {
    /**
     * Emits comic book states
     */
    val bookState: StateFlow<BookDescriptionState>

    /**
     * Emits comic book events
     */
    val eventsFlow: Flow<BookDescriptionEvent>

    val configState: StateFlow<ViewerConfigState>

    /**
     * Get comic book description
     * @param id comic book id
     */
    fun loadBookDescription(id: Long)

    /**
     * Set provided page as comic book cover
     * @param pagePosition page position to set as cover
     */
    fun setPageAsCover(pagePosition: Int)

    /**
     * Swap comic book read direction
     */
    fun swapDirection()

    /**
     * Save page as last read position
     * @param pagePosition comic book page position
     */
    fun saveReadPosition(pagePosition: Int)
}

class BookViewerViewModelImpl(
    private val bookViewerUseCase: BookViewerUseCase,
    private val viewerConfigUseCase: ViewerConfigUseCase,
    dispatchers: Dispatchers
) : CoroutineViewModel(dispatchers), BookViewerViewModel {
    //scope for all book related jobs
    private val bookJobScope: Job = Job(vmScope.coroutineContext[Job])

    private var loadingBookJob: Job? = null

    /**
     * Check current comic book valid state job
     */
    private var checkBookJob: Job? = null

    /**
     * Set comic book cover page job
     */
    private var setCoverJob: BookPageJob? = null

    private var saveReadPositionJob: BookPageJob? = null

    /**
     * Swicth comic book direction job
     */
    private var switchDirectionJob: Job? = null

    private val eventsSender = EventSender<BookDescriptionEvent>()

    override val bookState = MutableStateFlow<BookDescriptionState>(BookDescriptionState.Idle)

    override val configState = MutableStateFlow<ViewerConfigState>(ViewerConfigState.Loading)

    override val eventsFlow
        get() = eventsSender.eventState

    init {
        vmScope.launch {
            viewerConfigUseCase.configFlow()
                .collect { configState.value = ViewerConfigState.Loaded(it) }
        }
    }

    override fun loadBookDescription(id: Long) {
        when (val currentState = bookState.value) {
            is BookDescriptionState.Loaded -> {
                if (currentState.description.id == id) {
                    with(currentState.description) { checkComicBook(id, path) }

                    return
                }
            }

            is BookDescriptionState.Loading -> {
                if (currentState.id == id) {
                    return
                }
            }

            is BookDescriptionState.Corrupted, is BookDescriptionState.Idle, is BookDescriptionState.Error, BookDescriptionState.NotFound -> {
                // DO_NOTHING
            }
        }

        loadBookDescriptionInner(id)
    }

    override fun setPageAsCover(pagePosition: Int) {
        setCoverJob = startBookPageJob(
            requireBookDescription(), pagePosition, setCoverJob
        ) { bookId, containerPagePosition ->
            Logger.debug("Set new comic book ($bookId) cover position at $containerPagePosition")

            bookViewerUseCase.setPageAsCover(bookId, containerPagePosition)

            eventsSender.send(BookDescriptionEvent.CoverChanged(pagePosition))
        }
    }

    override fun swapDirection() {
        val currentState = bookState.value

        if (currentState is BookDescriptionState.Loaded) {
            val bookId = currentState.description.id

            val newDirection = when (currentState.description.direction) {
                Direction.LTR -> Direction.RTL
                Direction.RTL -> Direction.LTR
            }

            val previousSwitchJob = switchDirectionJob

            switchDirectionJob = vmScope.launch(bookJobScope) {
                previousSwitchJob?.cancelAndJoin()

                Logger.debug("Set comic book direction ($bookId, $newDirection)")

                bookViewerUseCase.updateDirection(bookId, newDirection)
            }
        }
    }

    override fun saveReadPosition(pagePosition: Int) {
        val book = requireBookDescription()

        //do not start job if we already save this page as last read position
        if (book.readPosition == pagePosition) {
            return
        }

        saveReadPositionJob = startBookPageJob(
            book, pagePosition, saveReadPositionJob
        ) { bookId, containerPagePosition ->
            Logger.debug("Save book ($bookId) read position at $containerPagePosition")

            bookViewerUseCase.saveReadPosition(bookId, containerPagePosition)
        }
    }

    private fun requireBookDescription(): ComicBookDescription =
        when (val currentBookState = bookState.value) {
            is BookDescriptionState.Loaded -> currentBookState.description
            else -> throw IllegalArgumentException("Can't get book description. Invalid state: $currentBookState")
        }

    private fun startBookPageJob(
        book: ComicBookDescription,
        pagePosition: Int,
        currentJob: BookPageJob?,
        body: suspend CoroutineScope.(bookId: Long, containerPagePosition: Long) -> Unit
    ): BookPageJob {
        val bookId = book.id
        val containerPagePosition = book.pages[pagePosition].position

        currentJob?.also {
            //do not run new job if we already run same
            if (it.isActive && it.pagePosition == containerPagePosition) {
                return currentJob
            } else {
                it.cancel()
            }
        }

        return BookPageJob(containerPagePosition, vmScope.launch(bookJobScope) {
            body(bookId, containerPagePosition)
        })
    }

    private fun checkComicBook(bookId: Long, bookPath: Uri) {
        //do not run same task twice
        if (checkBookJob?.isActive == false) {
            checkBookJob = vmScope.launch(bookJobScope) {
                Logger.debug("Check comic book ($bookId, $bookPath)")

                bookViewerUseCase.checkPersisted(bookId, bookPath)
            }
        }
    }

    private fun loadBookDescriptionInner(bookId: Long) {
        loadingBookJob = vmScope.launch(bookJobScope) {
            Logger.debug("Load new comic book ($bookId)")

            bookJobScope.children.forEach {
                if (it !== coroutineContext[Job]) {
                    it.cancelAndJoin()
                }
            }

            bookViewerUseCase.subscribe(bookId)
                .onStart { bookState.value = BookDescriptionState.Loading(bookId) }.transform {
                    emit(
                        when (it) {
                            null -> BookDescriptionState.NotFound
                            else -> if (it.persisted) {
                                BookDescriptionState.Loaded(it)
                            } else {
                                BookDescriptionState.Corrupted
                            }
                        }
                    )
                }.catch {
                    emit(
                        if (it is CancellationException) {
                            BookDescriptionState.Idle
                        } else {
                            BookDescriptionState.Error(it)
                        }
                    )
                }.collect { bookState.value = it }
        }
    }

    private data class BookPageJob(val pagePosition: Long, private val job: Job) : Job by job
}