/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
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

package app.seeneva.reader.extension

import android.graphics.Paint
import android.os.Build
import android.view.Gravity
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import app.seeneva.reader.R

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
    val background = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
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

        background
    } else {
        AppCompatResources.getDrawable(context, R.drawable.bcg_draggable_bottom_sheet)!!
    }

    setBackgroundPreservePadding(background)
}