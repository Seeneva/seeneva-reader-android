package com.almadevelop.comixreader.extension

import android.graphics.Paint
import android.os.Build
import android.view.Gravity
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import com.almadevelop.comixreader.R

/**
 * Create and set draggable background for a
 * [com.google.android.material.bottomsheet.BottomSheetDialogFragment] View
 *
 * @return draggable rounded background
 */
fun View.setDraggableBackground() {
    // [com.google.android.material.bottomsheet.BottomSheetBehavior] now responsible for round corners
    // I only need to draw 'draggable' indicator.
    // There is no purpose...I think it is more clearly that we can drag this View
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        val resources = context.resources

        val lineStrokeWidth = resources.getDimension(R.dimen.bottom_sheet_drag_line_stroke_width)
        val lineWidth = resources.getDimension(R.dimen.bottom_sheet_drag_line_width)
        val lineYOffset = resources.getDimension(R.dimen.bottom_sheet_drag_line_y_offset)

        val lineEdgeWidth = lineStrokeWidth * 0.5f

        val lineCy = lineStrokeWidth * 0.5f + lineYOffset

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = lineStrokeWidth
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = ContextCompat.getColor(context, R.color.black_alpha_20)
        }

        val background =
            createBitmap(lineWidth.toInt(), lineCy.toInt() * 2)
                .applyCanvas {
                    drawLine(
                        lineEdgeWidth,
                        lineCy,
                        lineWidth - lineEdgeWidth,
                        lineCy,
                        paint
                    )

                    setBitmap(null)
                }.toDrawable(resources).apply {
                    gravity = Gravity.CENTER_VERTICAL or Gravity.TOP
                }

        updatePadding(top = background.intrinsicHeight)

        ViewCompat.setBackground(this, background)
    } else {
        ViewCompat.setBackground(
            this,
            AppCompatResources.getDrawable(context, R.drawable.bcg_draggable_bottom_sheet)!!
        )
    }
}