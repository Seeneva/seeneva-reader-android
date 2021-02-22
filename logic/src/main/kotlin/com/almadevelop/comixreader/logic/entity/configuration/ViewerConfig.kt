package com.almadevelop.comixreader.logic.entity.configuration

import android.view.Window
import android.view.WindowManager

/**
 * Configuration for comic book viewer
 * @param keepScreenOn should keep screen ON
 * @param brightness viewer screen brightness
 * @param tts is text-to-speech enabled
 */
data class ViewerConfig(
    val keepScreenOn: Boolean = true,
    val brightness: Float = SYSTEM_BRIGHTNESS,
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
            brightness
        }
    }
}