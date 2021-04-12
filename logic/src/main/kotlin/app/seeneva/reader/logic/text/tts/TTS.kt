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

package app.seeneva.reader.logic.text.tts

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.logic.text.Language
import app.seeneva.reader.logic.usecase.ViewerConfigUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tinylog.kotlin.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Text To Speech
 */
interface TTS {
    val language: Language

    /**
     * Init TTS instance async
     */
    fun initAsync(): Deferred<InitResult>

    /**
     * Speak provided [text]
     * @param text text to speak
     */
    fun speakAsync(text: CharSequence): Deferred<Result>

    /**
     * Stop all speak tasks
     */
    fun stop()

    /**
     * Shutdown initialized TTS and close it
     * You can't use it anymore
     */
    fun close()

    sealed class Result {
        object Success : Result()
        object Disabled : Result()
        object Error : Result()
    }

    sealed class InitResult {
        /**
         * TTS was initialized
         */
        object Success : InitResult()

        /**
         * TTS engine is nit installed on device
         */
        object EngineNotInstalled : InitResult()

        /**
         * Language is not supported by TTS engine
         */
        object LanguageNotSupported : InitResult()
    }

    /**
     * TTS factory
     */
    interface Factory {
        /**
         * Create new TTS instance
         */
        fun new(type: Type): TTS

        /**
         * Create new instance of TTS error Resolver
         */
        fun newResolver(): TTSErrorResolver

        enum class Type { Default, Viewer }
    }
}

/**
 * New TTS for comic book viewer cases
 */
fun TTS.Factory.newViewerTTS() = new(TTS.Factory.Type.Viewer)

fun TTS.Factory.newTTS() = new(TTS.Factory.Type.Default)

/**
 * Success result
 */
val TTS.Result.success
    get() = this == TTS.Result.Success

private interface CoroutineTTS : TTS {
    val coroutineScope: CoroutineScope

    fun checkActive() {
        check(coroutineScope.isActive) { "TTS  was closed" }
    }
}

/**
 * Wrapper around default Android [TextToSpeech]
 */
internal class AndroidTTS(context: Context, dispatchers: Dispatchers) : CoroutineTTS {
    override val language: Language
        get() = Language.English

    private val context = context.applicationContext

    override val coroutineScope = CoroutineScope(dispatchers.io)

    /**
     * Parent [Job] for all TTS tasks to simplify cancellation
     */
    private val tasksParentJob = Job(coroutineScope.coroutineContext.job)

    private var tts: TextToSpeech? = null

    private val mutex = Mutex()

    override fun initAsync() =
        coroutineScope.async(tasksParentJob) { init().asInitResult() }

    override fun speakAsync(text: CharSequence): Deferred<TTS.Result> {
        checkActive()

        stop()

        return when {
            text.isBlank() -> CompletableDeferred(TTS.Result.Success)
            else -> coroutineScope.async(tasksParentJob) {
                when (val ttsInit = init()) {
                    is InitResultInner.Success -> speakInner(ttsInit.tts, text)
                    else -> TTS.Result.Error
                }
            }
        }
    }

    override fun stop() {
        tasksParentJob.cancelChildren()
        tts?.stop()
    }

    override fun close() {
        if (!coroutineScope.isActive) {
            return
        }

        coroutineScope.cancel()

        tts?.apply {
            shutdown()

            Logger.debug("TTS was shutdown")
        }

        tts = null
    }

    private suspend fun init(): InitResultInner =
        mutex.withLock {
            tts.let {
                if (it != null) {
                    InitResultInner.Success(it)
                } else {
                    Logger.debug("Start TTS initializing")

                    if (!engineInstalled()) {
                        TTS.InitResult.EngineNotInstalled.asErrorResultInner()
                    } else {
                        when (val newTTS = newTts()) {
                            null -> {
                                Logger.debug("TTS cannot be initialized")
                                TTS.InitResult.EngineNotInstalled.asErrorResultInner()
                            }
                            else -> try {
                                currentCoroutineContext().ensureActive()

                                when (newTTS.setLanguage(language.locale)) {
                                    TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                                        Logger.debug("TTS doesn't support support $language")

                                        newTTS.shutdown()

                                        TTS.InitResult.LanguageNotSupported.asErrorResultInner()
                                    }
                                    else -> {
                                        Logger.debug("TTS was initialized")

                                        tts = newTTS

                                        InitResultInner.Success(newTTS)
                                    }
                                }
                            } catch (t: Throwable) {
                                newTTS.shutdown()
                                tts = null
                                throw t
                            }
                        }
                    }
                }
            }
        }

    private suspend fun speakInner(tts: TextToSpeech, text: CharSequence): TTS.Result {
        val maxChars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            TextToSpeech.getMaxSpeechInputLength()
        } else {
            Int.MAX_VALUE
        }

        return if (text.length <= maxChars) {
            tts.speakCompat(text, TextToSpeech.QUEUE_FLUSH).asResult()
        } else {
            // I'm not sure if there is a lot of cases there comic book text is so long
            // So it is a simple implementation for such cases

            try {
                text.chunkedSequence(maxChars)
                    .asFlow()
                    .collectIndexed { index, txt ->
                        val result = tts.speakCompat(
                            txt,
                            if (index == 0) {
                                TextToSpeech.QUEUE_FLUSH
                            } else {
                                TextToSpeech.QUEUE_ADD
                            }
                        ).asResult()

                        if (result == TTS.Result.Error) {
                            //break flow in case of speak error
                            throw ChunkSpeakException
                        }
                    }

                TTS.Result.Success
            } catch (c: ChunkSpeakException) {
                TTS.Result.Error
            }
        }
    }

    /**
     * Get new TTS instance
     * @return null in case of error
     */
    private suspend fun newTts(): TextToSpeech? =
        suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null

            tts = TextToSpeech(context) {
                when (it) {
                    TextToSpeech.SUCCESS -> cont.resume(tts)
                    TextToSpeech.ERROR -> cont.resume(null)
                    else -> cont.resumeWithException(IllegalArgumentException("Unknown TTS init status: '$it'"))
                }
            }

            cont.invokeOnCancellation { tts.shutdown() }
        }

    /**
     * Check is TTS engine was installed
     */
    @SuppressLint("QueryPermissionsNeeded")
    private fun engineInstalled(): Boolean =
        Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
            .resolveActivity(context.packageManager) != null


    private object ChunkSpeakException : RuntimeException()

    private sealed class InitResultInner {
        data class Success(val tts: TextToSpeech) : InitResultInner()
        data class Error(val result: TTS.InitResult) : InitResultInner()
    }

    private fun InitResultInner.asInitResult() =
        when (this) {
            is InitResultInner.Success -> TTS.InitResult.Success
            is InitResultInner.Error -> result
        }

    private fun TTS.InitResult.asErrorResultInner() = InitResultInner.Error(this)

    private companion object {
        private const val SPEAK_ID = "page_object_txt"

        private fun Int.asResult() =
            when (this) {
                TextToSpeech.SUCCESS -> TTS.Result.Success
                TextToSpeech.ERROR -> TTS.Result.Error
                else -> throw IllegalArgumentException("Unknow TTS error code: '$this'")
            }

        /**
         * Speak provided [text].
         * @param text text to speak. It is better to use sentence cased text.
         * @param queueMode
         */
        private fun TextToSpeech.speakCompat(text: CharSequence, queueMode: Int) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                speak(text, queueMode, null, SPEAK_ID)
            } else {
                @Suppress("DEPRECATION")
                speak(text.toString(), queueMode, null)
            }
    }
}

/**
 * TTS wrapper for viewer use cases
 * It will automatically stop speech in case if TTS become disabled in settings
 */
private class ViewerTTS(
    private val inner: CoroutineTTS,
    useCase: ViewerConfigUseCase,
) : CoroutineTTS by inner {
    private val enabledFlow = useCase.configFlow()
        .map { it.tts }
        .onEach {
            if (!it) {
                stop()
            }
        }
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    init {
        //initialize TTS as soon as possible if it is enabled
        coroutineScope.launch {
            enabledFlow.filterNotNull()
                .collectLatest {
                    if (it) {
                        initAsync().await()
                    }
                }
        }
    }

    override fun speakAsync(text: CharSequence): Deferred<TTS.Result> {
        checkActive()

        return coroutineScope.async {
            val enabled = enabledFlow.value ?: enabledFlow.filterNotNull().first()

            if (enabled) {
                inner.speakAsync(text).let {
                    try {
                        it.await()
                    } finally {
                        it.cancel()
                    }
                }
            } else {
                TTS.Result.Disabled
            }
        }
    }

}

internal class TTSFactory(
    context: Context,
    private val dispatchers: Dispatchers,
    private val viewerUseCase: Lazy<ViewerConfigUseCase>
) : TTS.Factory {
    private val context = context.applicationContext

    override fun new(type: TTS.Factory.Type): TTS =
        when (type) {
            TTS.Factory.Type.Default -> AndroidTTS(context, dispatchers)
            TTS.Factory.Type.Viewer -> ViewerTTS(
                AndroidTTS(context, dispatchers),
                viewerUseCase.value
            )
        }

    override fun newResolver() =
        AndroidTTSErrorResolverImpl(context)
}