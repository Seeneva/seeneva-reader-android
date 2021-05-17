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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import app.seeneva.reader.logic.comic.AddComicBookMode
import app.seeneva.reader.logic.comic.ComicHelper
import app.seeneva.reader.router.ResultRouter
import app.seeneva.reader.router.RouterResultContext
import app.seeneva.reader.screen.viewer.BookViewerActivity
import app.seeneva.reader.viewmodel.EventSender
import kotlinx.coroutines.flow.Flow

sealed interface ComicListRouterResult {
    /**
     * User has been chose comic book to add into library
     * @param mode adding mode
     * @param paths comic book paths
     * @param flags [Intent] adding flags
     */
    data class ComicBookChose(val mode: AddComicBookMode, val paths: List<Uri>, val flags: Int) :
        ComicListRouterResult


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

class ComicListRouterImpl(private val view: RouterResultContext) : ComicListRouter {
    private val resultSender = EventSender<ComicListRouterResult>()

    override val resultFlow: Flow<ComicListRouterResult>
        get() = resultSender.eventState

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_VIEWER -> {
                when (resultCode) {
                    BookViewerActivity.RESULT_CORRUPTED -> resultSender.send(ComicListRouterResult.CorruptedComicBook)
                    BookViewerActivity.RESULT_NOT_FOUND -> resultSender.send(ComicListRouterResult.NonExistentBook)
                }
            }
            else -> {
                if (resultCode == Activity.RESULT_OK) {
                    val openMode = openModeFromRequestCode(requestCode)

                    if (openMode != null) {
                        requireNotNull(data) { "Comic books opening result doesn't have any data" }

                        val dataContent = data.data
                        val dataClipData = data.clipData

                        val paths = when {
                            dataContent != null -> listOf(dataContent)
                            dataClipData != null -> {
                                (0 until dataClipData.itemCount).map { dataClipData.getItemAt(it).uri }
                            }
                            else -> throw IllegalStateException("Result intent doesn't have any data $data")
                        }

                        resultSender.send(
                            ComicListRouterResult.ComicBookChose(
                                openMode,
                                paths,
                                data.flags
                            )
                        )
                    } else {
                        throw IllegalArgumentException("Unknown request code: $requestCode")
                    }
                }
            }
        }
    }

    override fun showComicBookSelector(mode: AddComicBookMode) =
        try {
            view.startActivityForResult(
                ComicHelper.openComicBookIntent(mode),
                openRequestCode(mode)
            )

            true
        } catch (_: ActivityNotFoundException) {
            false
        }

    override fun showComicBookViewer(bookId: Long) {
        view.startActivityForResult(
            BookViewerActivity.openViewer(view.context, bookId),
            REQUEST_VIEWER
        )
    }

    companion object {
        private const val REQUEST_ADD_COMIC_BOOK = 100
        private const val REQUEST_VIEWER = 200

        private fun openRequestCode(addComicBookMode: AddComicBookMode) =
            REQUEST_ADD_COMIC_BOOK or (addComicBookMode.ordinal + 1)

        private fun openModeFromRequestCode(requestCode: Int): AddComicBookMode? {
            AddComicBookMode.values().forEach {
                val id = it.ordinal + 1

                if (requestCode and id == id) {
                    return it
                }
            }

            return null
        }
    }
}