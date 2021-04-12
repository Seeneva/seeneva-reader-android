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

package app.seeneva.reader.logic.text.ocr

import android.graphics.Bitmap
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.data.source.jni.NativeSource
import app.seeneva.reader.logic.text.Language
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tinylog.kotlin.Logger
import app.seeneva.reader.data.entity.ml.Tesseract as TesseractInner

interface OCR {
    /**
     * Current language
     */
    val language: Language

    /**
     * Start recognize text on provided [Bitmap]
     * @param src source bitmap where txt should be recognized
     * @return async task
     */
    fun recognizeAsync(src: Bitmap): Deferred<String>

    /**
     * Close OCR and all recognize tasks
     */
    fun close()

    /**
     * OCR factory
     */
    interface Factory {
        /**
         * @return new OCR instance
         */
        fun new(): OCR
    }
}

internal class TesseractOCR(
    private val nativeSource: NativeSource,
    dispatchers: Dispatchers,
) : OCR {
    //TODO should allow change language in future
    override val language: Language
        get() = Language.English

    private val coroutineScope = CoroutineScope(dispatchers.io)

    private var inner: TesseractInner? = null

    private val mutex = Mutex()

    override fun recognizeAsync(src: Bitmap): Deferred<String> {
        check(coroutineScope.isActive) { "OCR was closed" }

        return coroutineScope.async {
            Logger.debug("Start text recognition on the Bitmap")

            // TODO: I have issue with Leptonica it is not support RGB_565.
            //  It is workaround for now.
            //  I should check what's going on there.
            val bitmap = when (src.config) {
                Bitmap.Config.ARGB_8888 -> src
                else -> src.copy(Bitmap.Config.ARGB_8888, false)
            }

            try {
                nativeSource.recognizeText(init(), bitmap)
                    .also { txt -> Logger.debug("Recognized text: $txt") }
            } finally {
                if (bitmap !== src) {
                    bitmap.recycle()
                }
            }
        }
    }

    override fun close() {
        if (!coroutineScope.isActive) {
            return
        }

        coroutineScope.cancel()

        inner?.close()
        inner = null
    }

    private suspend fun init() =
        mutex.withLock {
            inner.let {
                if (it != null) {
                    it
                } else {
                    Logger.debug("Tesseract initialization. Language: $language")

                    val tesseract = nativeSource.initTesseractFromAsset(
                        "${language.code}_seeneva.traineddata",
                        language.code
                    )

                    try {
                        currentCoroutineContext().ensureActive()

                        inner = tesseract

                        Logger.debug("Tesseract initialization finished. Language: $language")

                        tesseract
                    } catch (t: CancellationException) {
                        tesseract.close()
                        throw t
                    }
                }
            }
        }

    class Factory(
        private val nativeSource: NativeSource,
        private val dispatchers: Dispatchers,
    ) : OCR.Factory {
        override fun new(): TesseractOCR =
            TesseractOCR(nativeSource, dispatchers)
    }
}