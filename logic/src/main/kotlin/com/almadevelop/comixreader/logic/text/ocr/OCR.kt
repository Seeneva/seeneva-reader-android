package com.almadevelop.comixreader.logic.text.ocr

import android.graphics.Bitmap
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.data.source.jni.NativeSource
import com.almadevelop.comixreader.logic.text.Language
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tinylog.kotlin.Logger
import com.almadevelop.comixreader.data.entity.ml.Tesseract as TesseractInner

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
                        "${language.code}_comix.traineddata",
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