package com.almadevelop.comixreader.screen.viewer.page

import android.graphics.Bitmap
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.ComicsSettings
import com.almadevelop.comixreader.logic.entity.ComicPageData
import com.almadevelop.comixreader.logic.text.ocr.OCR
import com.almadevelop.comixreader.logic.usecase.GetPageDataUseCase
import com.almadevelop.comixreader.logic.usecase.text.RecognizeTextUseCase
import com.almadevelop.comixreader.viewmodel.CoroutineViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.Closeable

sealed class EncodedPageState {
    /**
     * Loaded state. It is also helper to prevent close encoded page image while [pageData] not closed
     */
    data class Loaded(
        val pageData: ComicPageData
    ) : EncodedPageState(), Closeable by pageData

    data class Loading(val pageId: Long) : EncodedPageState()
    data class Error(val t: Throwable) : EncodedPageState()

    object Idle : EncodedPageState()
}

/**
 * State of text recognition
 */
sealed class TxtRecognitionState {
    /**
     * No text recognition in progress
     */
    object Idle : TxtRecognitionState()

    /**
     * Text recognition in progress
     * @param objectId id of the page object
     */
    data class Process(val objectId: Long) : TxtRecognitionState()

    /**
     * Text was recognized
     */
    data class Recogized(val txt: String) : TxtRecognitionState()
}

interface BookViewerPageViewModel {
    val pageState: StateFlow<EncodedPageState>

    val txtRecognitionState: StateFlow<TxtRecognitionState>

    /**
     * Will emit recognized text on image
     */
    val txtRecognitionEvent: SharedFlow<TxtRecognitionState.Recogized>

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
        MutableSharedFlow<TxtRecognitionState.Recogized>(
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
        }

        loadPageDataInner(pageId)
    }

    override fun recognizeObjectText(ocr: OCR, objectId: Long, bitmap: Bitmap): Job {
        return vmScope.launch {
            _txtRecognitionState.value = TxtRecognitionState.Process(objectId)

            try {
                _txtRecognitionEvent.emit(
                    TxtRecognitionState.Recogized(
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