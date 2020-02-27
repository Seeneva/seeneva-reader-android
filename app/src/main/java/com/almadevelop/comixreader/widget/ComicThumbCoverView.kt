package com.almadevelop.comixreader.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.almadevelop.comixreader.logic.comic.ComicHelper
import kotlin.math.roundToInt

class ComicThumbCoverView : AppCompatImageView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)

        val desiredHeightSpec =
            MeasureSpec.makeMeasureSpec((width * ComicHelper.pageRatio).roundToInt(), MeasureSpec.EXACTLY)

        super.onMeasure(widthMeasureSpec, desiredHeightSpec)
    }
}