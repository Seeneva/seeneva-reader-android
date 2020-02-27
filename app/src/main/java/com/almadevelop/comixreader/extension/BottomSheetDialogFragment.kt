package com.almadevelop.comixreader.extension

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.shapes.RoundRectShape
import android.view.Gravity
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnLayout
import androidx.core.view.forEach
import androidx.core.view.updatePadding
import com.almadevelop.comixreader.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Set rounded draggable background
 *
 * Should be set after the onCreateView method call
 */
fun BottomSheetDialogFragment.setDraggableBackground() {
    requireNotNull(dialog).findViewById<ViewGroup>(R.id.design_bottom_sheet).doOnLayout { bottomSheetView ->
        require(bottomSheetView is ViewGroup)

        val background = newDraggableBackground(bottomSheetView.context, bottomSheetView.width)

        bottomSheetView.background = background

        bottomSheetView.forEach {
            it.updatePadding(top = background.intrinsicHeight)
        }
    }
}

/**
 * Create rounded draggable background
 *
 * @param context context
 * @param width width of the generated background drawable
 * @return draggable rounded background
 */
private fun newDraggableBackground(context: Context, width: Int): Drawable {
    val resources = context.resources

    val cornerRadius = resources.getDimension(R.dimen.bottom_sheet_drag_corner_radius)

    val lineWidth = resources.getDimension(R.dimen.bottom_sheet_drag_line_width)
    val lineHeight = resources.getDimension(R.dimen.bottom_sheet_drag_line_height)
    val lineYOffset = resources.getDimension(R.dimen.bottom_sheet_drag_line_y_offset)

    //line x center position
    val lineCx = width * 0.5f
    val lineCy = lineWidth * 0.5f + lineYOffset

    val bitmap = Bitmap.createBitmap(width, lineCy.toInt() * 2, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = ContextCompat.getColor(context, R.color.grey_50)

    //draw rounded corners
    RoundRectShape(
        floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, .0f, .0f, .0f, .0f),
        null,
        null
    ).also {
        it.resize(bitmap.width.toFloat(), bitmap.height.toFloat())
    }.draw(canvas, paint)

    //draw 'draggable' line
    paint.also {
        it.strokeWidth = lineWidth
        it.strokeJoin = Paint.Join.ROUND
        it.strokeCap = Paint.Cap.ROUND
        it.color = ContextCompat.getColor(context, R.color.black_alpha_20)
    }

    canvas.drawLine(
        lineCx - lineHeight * 0.5f,
        lineCy,
        lineCx + lineHeight * 0.5f,
        lineCy,
        paint
    )

    canvas.setBitmap(null)

    return bitmap.toDrawable(resources).also {
        it.gravity = Gravity.TOP
        it.setTileModeXY(Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
}