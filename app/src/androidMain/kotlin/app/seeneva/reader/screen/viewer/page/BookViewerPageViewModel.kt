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

package app.seeneva.reader.screen.viewer.page

import android.graphics.Bitmap
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.logic.ComicsSettings
import app.seeneva.reader.logic.entity.ComicPageData
import app.seeneva.reader.logic.text.ocr.OCR
import app.seeneva.reader.logic.usecase.GetPageDataUseCase
import app.seeneva.reader.logic.usecase.text.RecognizeTextUseCase
import app.seeneva.reader.viewmodel.CoroutineViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.Closeable

sealed interface EncodedPageState {
    /**
     * Loaded state. It is also helper to prevent close encoded page image while [pageData] not closed
     */
    data class Loaded(val pageData: ComicPageData) : EncodedPageState, Closeable by pageData

    data class Loading(val pageId: Long) : EncodedPageState
    data class Error(val t: Throwable) : EncodedPageState

    object Idle : EncodedPageState
}

/**
 * State of text recognition
 */
sealed interface TxtRecognitionState {
    /**
     * No text recognition in progress
     */
    object Idle : TxtRecognitionState

    /**
     * Text recognition in progress
     * @param objectId id of the page object
     */
    data class Process(val objectId: Long) : TxtRecognitionState

    /**
     * Text was recognized
     */
    data class Recognized(val txt: String) : TxtRecognitionState
}

interface BookViewerPageViewModel {
    val pageState: StateFlow<EncodedPageState>

    val txtRecognitionState: StateFlow<TxtRecognitionState>

    /**
     * Will emit recognized text on image
     */
    val txtRecognitionEvent: SharedFlow<TxtRecognitionState.Recognized>

    fun loadPageData(pageId: Long)

    /**
     * Recognize text on provided page object's [bitmap]
     * @param ocr OCR engine to use
     * @param objectId source page object's id
     * @param bitmap source object's bitmap where text should be recognized
     */
    fun recognizeObjectText(ocr: OCR, objectId: Long, bitmap: Bitmap): Job

    /**
     * Set is user has finished helping tips or not
     * @param finished
     */
    fun setHelpFinished(finished: Boolean)
}

class BookViewerPageViewModelImpl(
    private val getPageDataUseCase: GetPageDataUseCase,
    //User can never use text recognition, so put it into lazy initialization
    _recognizeTextUseCase: Lazy<RecognizeTextUseCase>,
    private val settings: ComicsSettings,
    dispatchers: Dispatchers
) : CoroutineViewModel(dispatchers), BookViewerPageViewModel {
    private val recognizeTextUseCase by _recognizeTextUseCase

    private val _txtRecognitionEvent =
        MutableSharedFlow<TxtRecognitionState.Recognized>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val _txtRecognitionState =
        MutableStateFlow<TxtRecognitionState>(TxtRecognitionState.Idle)

    private val _pageState = MutableStateFlow<EncodedPageState>(EncodedPageState.Idle)

    override val pageState = _pageState.asStateFlow()

    override val txtRecognitionState = _txtRecognitionState.asStateFlow()

    override val txtRecognitionEvent = _txtRecognitionEvent.asSharedFlow()

    private var loadPageJob: Job? = null

    override fun loadPageData(pageId: Long) {
        when (val currentEncodedState = pageState.value) {
            is EncodedPageState.Loaded -> {
                if (currentEncodedState.pageData.id == pageId) {
                    return
                } else {
                    //release page resources before load new page
                    currentEncodedState.close()
                }
            }

            is EncodedPageState.Loading -> {
                if (currentEncodedState.pageId == pageId) {
                    return
                }
            }

            is EncodedPageState.Error, EncodedPageState.Idle -> {
                // DO_NOTHING
            }
        }

        loadPageDataInner(pageId)
    }

    override fun recognizeObjectText(ocr: OCR, objectId: Long, bitmap: Bitmap): Job {
        return vmScope.launch {
            _txtRecognitionState.value = TxtRecognitionState.Process(objectId)

            try {
                _txtRecognitionEvent.emit(
                    TxtRecognitionState.Recognized(
                        recognizeTextUseCase.recognizePageObjectText(
                            objectId,
                            ocr,
                            bitmap
                        )
                    )
                )
            } finally {
                _txtRecognitionState.value = TxtRecognitionState.Idle
            }
        }
    }

    override fun setHelpFinished(finished: Boolean) {
        vmScope.launch { settings.setShouldShowViewerHelp(!finished) }
    }

    override fun onCleared() {
        super.onCleared()

        closeEncodedPage()
    }

    private fun loadPageDataInner(pageId: Long) {
        val prevLoadPageJob = loadPageJob

        loadPageJob = getPageDataUseCase.subscribePageData(pageId)
            .onStart {
                prevLoadPageJob?.cancelAndJoin()

                _pageState.value = EncodedPageState.Loading(pageId)
            }.map { EncodedPageState.Loaded(it) as EncodedPageState }
            .catch { emit(EncodedPageState.Error(it)) }
            .onCompletion {
                emit(EncodedPageState.Idle)
            }
            .onEach { _pageState.value = it }
            .launchIn(vmScope)
    }

    private fun closeEncodedPage() {
        val currentPageState = pageState.value

        if (currentPageState is EncodedPageState.Loaded) {
            currentPageState.close()
        }
    }
}