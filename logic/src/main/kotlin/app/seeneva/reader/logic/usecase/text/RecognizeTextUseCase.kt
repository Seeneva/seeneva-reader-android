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

package app.seeneva.reader.logic.usecase.text

import android.graphics.Bitmap
import app.seeneva.reader.data.source.local.db.dao.ComicPageObjectSource
import app.seeneva.reader.logic.text.SentenceBreakerFactory
import app.seeneva.reader.logic.text.ocr.OCR
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
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
                .map { seq ->
                    seq.lowercase(languageLocale).replaceFirstChar { it.uppercase(languageLocale) }
                }
                .collect { append(it) }
        }
    }
}