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

package app.seeneva.reader.screen.list

import android.content.ActivityNotFoundException
import androidx.annotation.MainThread
import androidx.core.os.bundleOf
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import app.seeneva.reader.extension.registerAndRestore
import app.seeneva.reader.logic.comic.AddComicBookMode
import app.seeneva.reader.logic.results.ChooseComicBookContract
import app.seeneva.reader.logic.results.ChooseComicBookResult
import app.seeneva.reader.router.ResultRouter
import app.seeneva.reader.router.RouterResultContext
import app.seeneva.reader.screen.viewer.BookViewerActivity
import app.seeneva.reader.viewmodel.EventSender
import kotlinx.coroutines.flow.Flow
import org.tinylog.kotlin.Logger

sealed interface ComicListRouterResult {
    /**
     * Add provided comic book(s) into the library
     * @param mode adding mode
     * @param result comic book chose result
     */
    data class AddComicBooks(
        val mode: AddComicBookMode,
        val result: ChooseComicBookResult
    ) : ComicListRouterResult

    /**
     * User tried to open corrupted comic book
     */
    object CorruptedComicBook : ComicListRouterResult

    /**
     * User tried to open nin existed comic book
     */
    object NonExistentBook : ComicListRouterResult
}

interface ComicListRouter : ResultRouter<ComicListRouterResult> {
    /**
     * Show add comic book selector
     * @param mode add comic book mode
     * @return true if showed
     */
    fun showComicBookSelector(mode: AddComicBookMode): Boolean

    /**
     * Show comic book viewer
     * @param bookId comic book id to open
     */
    fun showComicBookViewer(bookId: Long)
}

class ComicListRouterImpl(
    routerContext: RouterResultContext,
    savedStateRegistryOwner: SavedStateRegistryOwner
) : ComicListRouter, SavedStateRegistry.SavedStateProvider {
    private val resultSender = EventSender<ComicListRouterResult>()

    override val resultFlow: Flow<ComicListRouterResult>
        get() = resultSender.eventState

    private var lastComicSelectorMode: AddComicBookMode? = null

    private val comicBookSelectorLauncher =
        routerContext.registerForActivityResult(ChooseComicBookContract()) {
            if (it != null) {
                resultSender.send(
                    ComicListRouterResult.AddComicBooks(
                        checkNotNull(lastComicSelectorMode) { "Add mode cannot be null" },
                        it
                    )
                )
            }

            lastComicSelectorMode = null
        }

    private val comicBookViewerLauncher =
        routerContext.registerForActivityResult(BookViewerActivity.OpenViewerContract()) {
            if (it != null) {
                resultSender.send(
                    when (it) {
                        BookViewerActivity.ResultMessage.CORRUPTED -> ComicListRouterResult.CorruptedComicBook
                        BookViewerActivity.ResultMessage.NOT_FOUND -> ComicListRouterResult.NonExistentBook
                    }
                )
            }
        }

    init {
        registerAndRestore(savedStateRegistryOwner) {
            if (it != null) {
                lastComicSelectorMode = it.getSerializable(STATE_SELECTOR_MODE) as? AddComicBookMode
            }
        }
    }

    @MainThread
    override fun showComicBookSelector(mode: AddComicBookMode) =
        try {
            Logger.debug { "Start comic book selector with adding mode: '$mode'" }

            lastComicSelectorMode = mode

            comicBookSelectorLauncher.launch(mode)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }

    override fun showComicBookViewer(bookId: Long) {
        Logger.debug { "Start viewer for result by comic book id '$bookId'" }
        comicBookViewerLauncher.launch(bookId)
    }

    override fun saveState() =
        bundleOf(STATE_SELECTOR_MODE to lastComicSelectorMode)

    companion object {
        private const val STATE_SELECTOR_MODE = "selector_mode"
    }
}