package com.almadevelop.comixreader.logic.usecase.text

import android.graphics.Bitmap
import com.almadevelop.comixreader.data.source.local.db.dao.ComicPageObjectSource
import com.almadevelop.comixreader.logic.text.SentenceBreakerFactory
import com.almadevelop.comixreader.logic.text.ocr.OCR
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface RecognizeTextUseCase {
    /**
     * Recognize text on comic book page object
     * @param objectId comic book page object id
     * @param ocr OCR engine to use
     * @param bitmap object source bitmap
     * @return recognized text or empty [String] if no text was recognized
     */
    suspend fun recognizePageObjectText(objectId: Long, ocr: OCR, bitmap: Bitmap): String
}

internal class RecognizeTextUseCaseImpl(
    private val pageObjectSource: ComicPageObjectSource,
    private val sentenceBreakerFactory: SentenceBreakerFactory,
) : RecognizeTextUseCase {
    override suspend fun recognizePageObjectText(objectId: Long, ocr: OCR, bitmap: Bitmap): String {
        val languageCode = ocr.language.code
        val languageLocale = ocr.language.locale

        val text = pageObjectSource.getRecognizedText(objectId, languageCode)
            ?: ocr.recognizeAsync(bitmap).let { recognizeJob ->
                try {
                    recognizeJob.await()
                } finally {
                    recognizeJob.cancel()
                }
            }.also {
                currentCoroutineContext().ensureActive()

                pageObjectSource.setRecognizedText(objectId, languageCode, it)
            }

        // break text into sequences, make them title case and combine into new string
        return buildString {
            sentenceBreakerFactory.get(languageLocale)
                .breakText(text)
                .map { it.toLowerCase(languageLocale).capitalize(languageLocale) }
                .collect { append(it) }
        }
    }
}