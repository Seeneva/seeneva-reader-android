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

package app.seeneva.reader.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import app.seeneva.reader.logic.comic.ComicHelper
import com.google.android.material.imageview.ShapeableImageView
import kotlin.math.roundToInt

/**
 * Custom [AppCompatImageView] which will calculate view dimensions to display comic book cover image
 */
class ComicCoverView : ShapeableImageView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        if (heightMode == MeasureSpec.EXACTLY && widthMode == MeasureSpec.EXACTLY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } else if (heightMode != MeasureSpec.UNSPECIFIED) {
            setMeasuredDimension(
                (heightSize / ComicHelper.PAGE_RATIO).roundToInt(),
                heightSize
            )
        } else if (widthMode != MeasureSpec.UNSPECIFIED) {
            setMeasuredDimension(
                widthSize,
                (widthSize * ComicHelper.PAGE_RATIO).roundToInt()
            )
        } else {
            throw IllegalStateException("Provide at least one dimension")
        }
    }
}