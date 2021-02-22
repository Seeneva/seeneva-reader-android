package com.almadevelop.comixreader.logic.entity.configuration

import org.json.JSONObject
import org.tinylog.Logger

private object Key {
    const val KEEP_SCREEN_ON = "keep_screen_on"
    const val BRIGHTNESS = "brightness"
    const val TTS = "tts"
}

/**
 * Serialize viewer configuration into [String]
 */
internal fun ViewerConfig.serialize(): String =
    runCatching {
        JSONObject()
            .also { obj ->
                obj.put(Key.KEEP_SCREEN_ON, keepScreenOn)
                obj.put(Key.BRIGHTNESS, brightness.toDouble())
                obj.put(Key.TTS, tts)
            }.toString()
    }.onFailure {
        Logger.error(it, "Can't serialize $this")
    }.getOrThrow()

/**
 * Deserialize [String] to [ViewerConfig]
 */
internal fun deserializeViewerConfiguration(input: String?): ViewerConfig? =
    if (!input.isNullOrEmpty()) {
        runCatching {
            JSONObject(input).let { obj ->
                ViewerConfig(
                    obj.optBoolean(Key.KEEP_SCREEN_ON, true),
                    obj.optDouble(
                        Key.BRIGHTNESS,
                        ViewerConfig.SYSTEM_BRIGHTNESS.toDouble()
                    ).toFloat(),
                    obj.optBoolean(Key.TTS, true),
                )
            }
        }.onFailure {
            Logger.error(it, "Can't deserialize ${ViewerConfig::class.java}")
        }.getOrThrow()
    } else {
        null
    }