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

package app.seeneva.reader.screen.viewer.page

import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import androidx.core.graphics.toRect
import androidx.core.os.bundleOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.extension.observe
import app.seeneva.reader.logic.ComicsSettings
import app.seeneva.reader.logic.entity.ComicPageData
import app.seeneva.reader.logic.entity.ComicPageObject
import app.seeneva.reader.logic.entity.ComicPageObjectContainer
import app.seeneva.reader.logic.entity.Direction
import app.seeneva.reader.logic.image.ImageLoader
import app.seeneva.reader.logic.text.ocr.OCR
import app.seeneva.reader.logic.text.tts.TTS
import app.seeneva.reader.presenter.BaseStatefulPresenter
import app.seeneva.reader.presenter.Presenter
import app.seeneva.reader.screen.viewer.page.entity.PageObjectDirection
import app.seeneva.reader.screen.viewer.page.entity.SelectedPageObject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.tinylog.kotlin.Logger

interface BookViewerPagePresenter : Presenter {
    val encodedPageState: Flow<EncodedPageState>

    /**
     * Emit `true` if help should be showed on the page, `false` otherwise
     */
    val showHelpFlow: SharedFlow<Boolean>

    /**
     * Represent current read direction state. null in case if current direction is unknown
     */
    val readDirectionState: StateFlow<Direction?>

    /**
     * Flow of text recognition events
     */
    val txtRecognition: SharedFlow<TxtRecognitionState>

    /**
     * Request next page object by provided direction
     * @param direction object read direction
     */
    fun nextPageObject(direction: PageObjectDirection): SelectedPageObject?

    /**
     * Request current page object
     */
    fun currentPageObject(): SelectedPageObject?

    /**
     * Reset currently read page object position
     */
    fun resetReadPageObject()

    /**
     * User perform long press event on the page image
     * @param x X position of touch
     * @param y Y position of touch
     * @return true if long press will perform some action
     */
    fun onPageLongClick(x: Float, y: Float): Boolean

    /**
     * User perform long press event on the currently showed page object
     * @return true if long press will perform some action
     */
    fun onCurrentPageObjectLongClick(): Boolean

    /**
     * User has finished all helping tips
     */
    fun onUserFinishHelpTips()
}

class BookViewerPagePresenterImpl(
    view: BookViewerPageView,
    dispatchers: Dispatchers,
    settings: ComicsSettings,
    private val imageLoader: ImageLoader,
    _ocr: Lazy<OCR>,
    //I use non Lazy<TTS> to warm up it as soon as possible
    //e.g. Google TTS has some delay before full initialization, other available TTS seems don't have this delay
    private val tts: TTS,
    private val viewModel: BookViewerPageViewModel,
    pageId: Long
) : BaseStatefulPresenter<BookViewerPageView>(view, dispatchers), BookViewerPagePresenter {
    private val ocr by _ocr

    override val encodedPageState
        get() = viewModel.pageState

    override val showHelpFlow =
        settings.shouldShowViewerHelpFlow()
            .flatMapLatest { showHelp ->
                if (showHelp) {
                    encodedPageState.map {
                        when (it) {
                            is EncodedPageState.Loaded -> it.pageData.objects.isNotEmpty()
                            else -> false
                        }
                    }
                } else {
                    flowOf(false)
                }
            }.shareIn(presenterScope, SharingStarted.WhileSubscribed())

    override val readDirectionState =
        encodedPageState.filterIsInstance<EncodedPageState.Loaded>()
            .map { it.pageData.objects.direction }
//            .distinctUntilChanged()
//            .drop(1)
            .stateIn(viewScope, SharingStarted.WhileSubscribed(), null)

    private val _txtRecognition =
        MutableSharedFlow<TxtRecognitionState>(0, 1, BufferOverflow.DROP_OLDEST)

    override val txtRecognition = _txtRecognition.asSharedFlow()

    /**
     * Current viewed page object position
     */
    private var readObjectPosition = -1

    private val pageData
        get() = viewModel.pageState.value.let { it as? EncodedPageState.Loaded }?.pageData

    private var txtRecognitionJob: Job? = null

    init {
        viewModel.loadPageData(pageId)

        // combine txt recognition states and events into single flow
        presenterScope.launch {
            _txtRecognition.emitAll(
                flowOf(
                    viewModel.txtRecognitionState,
                    viewModel.txtRecognitionEvent
                ).flattenMerge()
            )
        }

        txtRecognition.filterIsInstance<TxtRecognitionState.Recognized>()
            .map { it.txt }
            .filterNot { it.isEmpty() }
            .observe(view) { tts.speakAsync(it).await() }

        view.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                //prevent speech if page is paused
                tts.stop()
            }
        })
    }

    override fun onCreate(state: Bundle?) {
        if (state != null) {
            readObjectPosition = state.getInt(STATE_READ_POSITION, -1)
        }

        presenterScope.launch {
            //prefetch all objects on the page
            encodedPageState.filterIsInstance<EncodedPageState.Loaded>()
                .map { it.pageData }
                .filterNot { it.objects.isEmpty() }
                .collectLatest { prefetchPageObjects(it) }
        }

        readDirectionState.filterNotNull()
            .drop(1)
            .onEach {
                //Direction was changed, reset read position
                resetReadPageObject()
            }
            .launchIn(presenterScope)
    }

    override fun saveState() =
        bundleOf(STATE_READ_POSITION to readObjectPosition)

    override fun nextPageObject(direction: PageObjectDirection): SelectedPageObject? {
        val nextObject = when (direction) {
            PageObjectDirection.FORWARD -> {
                readObjectPosition + 1
            }
            PageObjectDirection.BACKWARD -> {
                readObjectPosition - 1
            }
        }

        return requirePageData().intoSelectedPageObject(nextObject)
            ?.also { readObjectPosition = nextObject }
    }

    override fun currentPageObject() =
        requirePageData().intoSelectedPageObject(readObjectPosition)

    override fun resetReadPageObject() {
        readObjectPosition = -1
    }

    override fun onPageLongClick(x: Float, y: Float) =
        onPageClickInner { it[x, y].firstOrNull() }

    override fun onCurrentPageObjectLongClick() =
        onPageClickInner { it.getOrNull(readObjectPosition) }

    override fun onUserFinishHelpTips() {
        viewModel.setHelpFinished(true)
    }

    private inline fun onPageClickInner(obj: (ComicPageObjectContainer) -> ComicPageObject?): Boolean {
        val page = requirePageData()

        if (page.objects.isEmpty()) {
            return false
        }

        val clickedObject = obj(page.objects) ?: return false

        val bbox = clickedObject.bbox.toRect()

        presenterScope.recognizeObjectText(clickedObject.id, bbox)

        return true
    }

    private fun requirePageData() =
        checkNotNull(pageData) { "Comic book page data is not loaded yet" }

    /**
     * Prefetch all page object images
     * @param page source page
     * @param batchSize how much objects will be processed simultaneously
     */
    private suspend fun prefetchPageObjects(page: ComicPageData, batchSize: Int = 3) {
        if (page.objects.isEmpty()) {
            return
        }

        val bookPath: Uri
        val pagePos: Long

        page.img.borrowedObject().also { img ->
            bookPath = img.path
            pagePos = img.position
        }

        // parallel prefetch page objects cropped images
        page.objects
            .asFlow()
            .map { presenterScope.launchPrefetchObjectImg(bookPath, pagePos, it.bbox.toRect()) }
            .buffer(batchSize)
            .collect { it.join() }
    }

    /**
     * Recognize page object's text located on provided bounding box
     * @param objectId page object id
     * @param objectBbox bounding box where text should be recognized
     */
    private fun CoroutineScope.recognizeObjectText(objectId: Long, objectBbox: Rect) {
        when (val recognizeState = viewModel.txtRecognitionState.value) {
            is TxtRecognitionState.Process -> {
                if (recognizeState.objectId == objectId) {
                    //it is the same page object. Do not start a new job
                    return
                }
            }
        }

        val page = requirePageData()

        val bookPath: Uri
        val pagePos: Long

        page.img.borrowedObject().also { img ->
            bookPath = img.path
            pagePos = img.position
        }

        val prevTxtRecognitionJob = txtRecognitionJob

        txtRecognitionJob = launch {
            //cancel any previous started tasks
            prevTxtRecognitionJob?.cancelAndJoin()

            //wait until text recognition will be on Idle state and start a new one
            //txtRecognition.filterIsInstance<TxtRecognitionState.Idle>().first()

            _txtRecognition.emit(TxtRecognitionState.Process(objectId))

            val bitmap = imageLoader.loadPageObjectBitmap(
                bookPath,
                pagePos,
                objectBbox,
            )

            ensureActive()

            val recognizeJob = viewModel.recognizeObjectText(ocr, objectId, bitmap)

            try {
                recognizeJob.join()
            } finally {
                if (presenterScope.isActive) {
                    //cancel only if presenter scope is still active
                    // (e.g. new recognize task started while this one is still active)
                    // It will allow continue in case of config change
                    recognizeJob.cancel()
                }
            }
        }
    }

    /**
     * Create prefetch job for single page object
     */
    private fun CoroutineScope.launchPrefetchObjectImg(
        bookPath: Uri,
        pagePos: Long,
        bbox: Rect
    ): Job =
        launch {
            try {
                imageLoader.loadPageObjectBitmap(bookPath, pagePos, bbox)
            } catch (t: Throwable) {
                Logger.error(
                    t,
                    "Can't prefetch page object. Book: $bookPath, page: $pagePos, bbox: ${bbox.toShortString()}"
                )
            }
        }

    /**
     * Return page object as selected page object by position
     * @param pos object position
     * @return comic book page object as selected object
     */
    private fun ComicPageData.intoSelectedPageObject(pos: Int): SelectedPageObject? {
        val obj = objects.getOrNull(pos) ?: return null

        return img.borrowedObject()
            .let { SelectedPageObject(it.path, it.position, obj.bbox) }
    }

    companion object {
        private const val STATE_READ_POSITION = "read_position"
    }
}