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

package app.seeneva.reader.screen.viewer

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.presenter.BasePresenter
import app.seeneva.reader.presenter.Presenter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

interface BookViewerPresenter : Presenter {
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
     * Current page was changed
     * @param pagePosition view's page position
     */
    fun onPageChange(pagePosition: Int)
}

class BookViewerPresenterImpl(
    view: BookViewerView,
    dispatchers: Dispatchers,
    private val viewModel: BookViewerViewModel,
    private val bookId: Long
) : BasePresenter<BookViewerView>(view, dispatchers), BookViewerPresenter {
    init {
        view.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                //I use onStart to periodically check loaded comic book file state (e.g. it was deleted for some reason)

                viewModel.loadBookDescription(bookId)
            }
        })

        viewScope.launch {
            view.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.configState
                        .filterIsInstance<ViewerConfigState.Loaded>()
                        .collect { view.onConfigChanged(it.config) }
                }

                launch {
                    viewModel.eventsFlow
                        .collect {
                            when (it) {
                                is BookDescriptionEvent.CoverChanged -> view.onCoverChanged()
                            }
                        }
                }

                launch {
                    viewModel.bookState
                        .collect {
                            when (it) {
                                is BookDescriptionState.Loaded -> view.onBookLoaded(it.description)
                                is BookDescriptionState.Corrupted -> view.onBookCorruption()
                                is BookDescriptionState.NotFound -> view.onBookNotFound()
                                is BookDescriptionState.Loading, is BookDescriptionState.Idle -> view.onBookLoading()
                                is BookDescriptionState.Error -> view.onBookLoadError()
                            }
                        }
                }
            }
        }
    }

    override fun setPageAsCover(pagePosition: Int) {
        viewModel.setPageAsCover(pagePosition)
    }

    override fun swapDirection() {
        viewModel.swapDirection()
    }

    override fun onPageChange(pagePosition: Int) {
        //set current read position
        viewModel.saveReadPosition(pagePosition)
    }
}