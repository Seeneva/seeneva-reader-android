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

package app.seeneva.reader.screen.viewer

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * [ViewPager2.PageTransformer] that approximates a real paper page turn:
 *
 * - The page currently being left behind pivots around its trailing edge (like the
 *   spine of a book) and rotates away in 3D (via [View.rotationY]), instead of just
 *   sliding off screen flat.
 * - It darkens slightly as it turns, mimicking the paper's underside catching less light.
 * - The incoming page is revealed from underneath with a subtle scale/slide-in and a
 *   soft drop shadow (via [View.translationZ]/elevation), as if it was resting under
 *   the page that just turned.
 *
 * This is a lightweight, view-property-based effect (cheap, GPU-friendly, works with
 * ViewPager2's Fragment-backed pages) rather than a true cloth/paper curl simulation,
 * which would need a custom Canvas/OpenGL view. It reads well at normal swipe speed and
 * needs no changes to the page Fragments themselves.
 *
 * @param pageWidthPx width of a page in pixels, used to pick a sensible camera distance.
 *                    Pass 0 to let each page compute it from its own measured width.
 */
class PageTurnTransformer(private val pageWidthPx: Int = 0) : ViewPager2.PageTransformer {

    companion object {
        /** How far the outgoing page rotates around its spine, in degrees, at full swipe. */
        private const val MAX_ROTATION_DEGREES = 60f

        /** Smallest scale the incoming page starts at before settling to 1f. */
        private const val MIN_INCOMING_SCALE = 0.92f

        /** How dark the outgoing page gets at the peak of its turn (1f = no darkening). */
        private const val MIN_BRIGHTNESS_ALPHA = 0.72f

        /** Depth used for the incoming page's drop shadow while it's still "underneath". */
        private const val MAX_TRANSLATION_Z_PX = 18f

        /** Multiplier controlling camera perspective strength; higher = flatter/subtler. */
        private const val CAMERA_DISTANCE_MULTIPLIER = 24f
    }

    override fun transformPage(page: View, position: Float) {
        val width = if (pageWidthPx > 0) pageWidthPx else page.width
        if (width == 0) {
            // Not measured yet, nothing sensible to do
            return
        }

        // Give the rotation a 3D perspective feel. Must be set before rotationY.
        page.cameraDistance = width * CAMERA_DISTANCE_MULTIPLIER

        when {
            // Fully off-screen to the left: fully turned page, hide it
            position < -1f -> {
                page.alpha = 0f
            }

            // Page is being turned away (was current, now moving to the left,
            // as if lifting up and rotating around its right/trailing edge)
            position <= 0f -> {
                val fraction = -position // 0f (current) -> 1f (fully turned)

                page.apply {
                    alpha = 1f
                    translationX = 0f
                    translationZ = 0f
                    pivotX = w(this)
                    pivotY = h(this) / 2f
                    rotationY = -MAX_ROTATION_DEGREES * fraction
                    scaleX = 1f
                    scaleY = 1f
                    // simulate the shaded underside of the paper as it lifts
                    setLayerAlphaTint(this, lerp(1f, MIN_BRIGHTNESS_ALPHA, fraction))
                }
            }

            // Page is incoming from the right, revealed from "under" the previous page
            position <= 1f -> {
                val fraction = 1f - position // 0f (fully hidden) -> 1f (now current)

                page.apply {
                    alpha = 1f
                    rotationY = 0f
                    pivotX = 0f
                    pivotY = h(this) / 2f
                    val scale = lerp(MIN_INCOMING_SCALE, 1f, fraction)
                    scaleX = scale
                    scaleY = scale
                    // slight slide so it doesn't pop straight to full size
                    translationX = -width * position * 0.08f
                    translationZ = lerp(MAX_TRANSLATION_Z_PX, 0f, fraction)
                    setLayerAlphaTint(this, 1f)
                }
            }

            // Fully off-screen to the right
            else -> {
                page.alpha = 0f
            }
        }
    }

    private fun w(view: View) = view.width.toFloat().let { if (it == 0f) 1f else it }
    private fun h(view: View) = view.height.toFloat().let { if (it == 0f) 1f else it }

    private fun lerp(start: Float, end: Float, fraction: Float) =
        start + (end - start) * fraction.coerceIn(0f, 1f)

    /**
     * Cheap "darkening" without allocating a ColorMatrix filter every frame:
     * nudge the view's own alpha slightly along with a background scrim would need
     * a layout change, so we approximate with alpha only, clamped so the page never
     * fully disappears while still part-visible.
     */
    private fun setLayerAlphaTint(view: View, brightness: Float) {
        view.alpha = max(min(brightness, 1f), 0.35f)
    }
}
