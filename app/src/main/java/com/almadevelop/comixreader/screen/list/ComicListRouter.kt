package com.almadevelop.comixreader.screen.list

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.almadevelop.comixreader.logic.comic.AddComicBookMode
import com.almadevelop.comixreader.logic.comic.ComicHelper
import com.almadevelop.comixreader.router.ResultRouter
import com.almadevelop.comixreader.router.RouterResultContext
import com.almadevelop.comixreader.screen.viewer.BookViewerActivity
import com.almadevelop.comixreader.viewmodel.EventSender
import kotlinx.coroutines.flow.Flow

sealed class ComicListRouterResult {
    /**
     * User has been chose comic book to add into library
     * @param mode adding mode
     * @param paths comic book paths
     * @param flags [Intent] adding flags
     */
    data class ComicBookChose(val mode: AddComicBookMode, val paths: List<Uri>, val flags: Int) :
        ComicListRouterResult()


    /**
     * User tried to open corrupted comic book
     */
    object CorruptedComicBook : ComicListRouterResult()

    /**
     * User tried to open nin existed comic book
     */
    object NonExistentBook : ComicListRouterResult()
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