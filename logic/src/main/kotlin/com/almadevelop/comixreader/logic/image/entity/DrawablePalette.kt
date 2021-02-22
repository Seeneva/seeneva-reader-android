package com.almadevelop.comixreader.logic.image.entity

import android.graphics.drawable.Drawable
import androidx.palette.graphics.Palette

/**
 * [Drawable] with calculated [Palette]
 */
data class DrawablePalette(val drawable: Drawable, val palette: Palette)