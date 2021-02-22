package com.almadevelop.comixreader.logic.text.tts

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.core.net.toUri

/**
 * Helper to resolve TTS errors
 */
interface TTSErrorResolver {
    /**
     * Resolve TTS init error
     * @param initResult init result to resolve
     * @return true if it was resolved
     */
    fun resolve(initResult: TTS.InitResult): Boolean

    /**
     * Check is TTS init error can be resolved somehow
     * @param initResult init result to resolve
     * @return true if it can be resolved
     */
    fun canResolve(initResult: TTS.InitResult): Boolean
}

internal class AndroidTTSErrorResolverImpl(
    private val context: Context,
) : TTSErrorResolver {
    //all I can do here is to start some Activities and believe that user install needed apps and data

    override fun resolve(initResult: TTS.InitResult) =
        when (val intent = initResult.resolveIntent()) {
            null -> true
            else -> resolveDefault(intent)
        }

    override fun canResolve(initResult: TTS.InitResult) =
        initResult.resolveIntent()?.canResolve() ?: true

    private fun resolveDefault(intent: Intent): Boolean =
        try {
            //all intents will be launched in new task, so context is not necessarily should be Activity
            //It is look like a normal behaviour here
            context.startActivity(intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

            true
        } catch (_: ActivityNotFoundException) {
            false
        }

    @SuppressLint("QueryPermissionsNeeded")
    private fun Intent.canResolve() =
        resolveActivity(context.packageManager) != null

    private fun TTS.InitResult.resolveIntent() =
        when (this) {
            TTS.InitResult.Success -> null
            TTS.InitResult.EngineNotInstalled ->
                Intent(
                    Intent.ACTION_VIEW,
                    "market://search?q=text-to-speech&c=apps".toUri()
                )
            TTS.InitResult.LanguageNotSupported ->
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        }
}