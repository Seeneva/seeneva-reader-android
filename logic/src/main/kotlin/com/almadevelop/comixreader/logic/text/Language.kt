package com.almadevelop.comixreader.logic.text

import java.util.*

/**
 * Available Tesseract languages
 */
enum class Language(
    internal val code: String,
    val locale: Locale
) {
    English(
        "eng",
        Locale.US
    )
}