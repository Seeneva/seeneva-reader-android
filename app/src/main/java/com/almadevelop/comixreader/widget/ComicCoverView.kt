package com.almadevelop.comixreader.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.almadevelop.comixreader.logic.comic.ComicHelper
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