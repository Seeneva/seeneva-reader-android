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