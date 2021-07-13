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

package app.seeneva.reader.logic.entity.configuration

import android.view.Window
import android.view.WindowManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for comic book viewer
 * @param keepScreenOn should keep screen ON
 * @param brightness viewer screen brightness
 * @param tts is text-to-speech enabled
 */
@Serializable
data class ViewerConfig(
    @SerialName("keep_screen_on")
    val keepScreenOn: Boolean = true,
    @SerialName("brightness")
    val brightness: Float = SYSTEM_BRIGHTNESS,
    @SerialName("tts")
    val tts: Boolean = true
) {
    val systemBrightness
        get() = brightness == SYSTEM_BRIGHTNESS

    companion object {
        const val SYSTEM_BRIGHTNESS = -1.0f
    }
}

/**
 * Apply viewer settings to Android Window
 */
fun ViewerConfig.applyToWindow(window: Window) {
    if (keepScreenOn) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    window.attributes = window.attributes.also { attrs ->
        attrs.screenBrightness = if (systemBrightness) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            brightness.coerceIn(.0f, 1.0f)
        }
    }
}