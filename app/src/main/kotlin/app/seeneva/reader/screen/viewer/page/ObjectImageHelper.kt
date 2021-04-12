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

package app.seeneva.reader.screen.viewer.page

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.transform
import androidx.core.view.*
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import app.seeneva.reader.R
import app.seeneva.reader.extension.addOnStateChangedListener
import app.seeneva.reader.logic.image.ImageLoader
import app.seeneva.reader.logic.image.ImageLoadingTask
import app.seeneva.reader.screen.viewer.page.entity.SelectedPageObject
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

/**
 * Helper to show comic book objects as pop in images
 * @param scaleImageView where source page image displayed
 * @param objectImageView where show page object
 * @param imageLoader needed to load cropped object's image
 */
class ObjectImageHelper(
    private val scaleImageView: SubsamplingScaleImageView,
    private val objectImageView: ImageView,
    private val imageLoader: ImageLoader,
) {
    // reusable purpose
    private val point = PointF()

    /**
     * Source object box scaled and positioned relative to showed page image (view coordinates)
     */
    private val viewBbox = RectF()

    /**
     * Rounded source object bounding box (image coordinates)
     */
    private val srcRndBbox = Rect()

    private val matrix = Matrix()

    private val interpolator = FastOutSlowInInterpolator()

    private val context
        get() = scaleImageView.context

    /**
     * Display it in case of loading image error
     */
    private val error =
        context.obtainStyledAttributes(intArrayOf(R.attr.colorError))
            .use { it.getColor(0, ContextCompat.getColor(context, R.color.red_800)) }
            .toDrawable()

    private val baseAnimator
        get() = ViewCompat.animate(objectImageView)
            .setDuration(200L)
            .setInterpolator(interpolator)

    /**
     * Last object image loading task
     */
    private var loadingTask: ImageLoadingTask? = null

    private var animationState = ObjectAnimState.IDLE

    /**
     * Additional task on hide animation end
     */
    private var onHidingAnimEnd: (() -> Unit)? = null

    /**
     * Cached [scaleImageView]'s center value
     */
    private val centerCache = PointF(Float.MIN_VALUE, Float.MIN_VALUE)

    private val scaleViewStateListener = object :
        SubsamplingScaleImageView.OnStateChangedListener {

        override fun onScaleChanged(newScale: Float, origin: Int) {
            startBlowingAnimation()
        }

        override fun onCenterChanged(newCenter: PointF, origin: Int) {
            // On my Android 16 tablet this listener invoked every time I touch the screen
            // So I check here that center is REALLY changed to prevent animation
            if (!centerCache.equals(newCenter.x, newCenter.y)) {
                centerCache.set(newCenter)
                startBlowingAnimation()
            }
        }
    }

    /**
     * Show object on the page
     * @param objectData single comic book page object data
     * @param scaleXY will be applied to object image.
     * Can be less than required in case if scaled balloon will be outside of visible area
     * @param animate is page object should be showed using animation
     */
    fun showPageObject(
        objectData: SelectedPageObject,
        scaleXY: Float = context.resources.getDimension(R.dimen.viewer_balloon_scale_xy),
        animate: Boolean = true
    ) {
        check(scaleImageView.isReady) { "Image is not ready" }

        // A little bit hacky, but fast.
        //
        // Without it onCenterChanged can be called after every touch event
        // I can get scaleImageView.center only after it was initialized.
        // But I can't use setOnImageEventListener 'cause it is already in use
        // Should be refactored
        if (centerCache.x == Float.MIN_VALUE) {
            //save view center on first object show
            centerCache.set(scaleImageView.center!!)

            scaleImageView.addOnStateChangedListener(scaleViewStateListener)
        }

        val resultScaleXY = scaleImageView.minScale + scaleXY

        /**
         * Switch one showed object to another with animation
         */
        fun switchObject() {
            if (objectImageView.isVisible && animate) {
                onHidingAnimEnd = { showPageObjectInner(objectData, resultScaleXY, animate) }

                if (animationState != ObjectAnimState.HIDING) {
                    //we need to hide current visible balloon firstly
                    baseAnimator.scaleX(.0f)
                        .scaleY(.0f)
                        .objectTranslationBy(viewBbox, false)
                        .withStartAction { animationState = ObjectAnimState.HIDING }
                        .withEndAction {
                            animationState = ObjectAnimState.IDLE

                            objectImageView.isGone = true

                            loadingTask?.dispose()

                            //we are ready to load new balloon
                            onHidingAnimEnd?.invoke()
                            onHidingAnimEnd = null
                        }.start()
                }
            } else {
                showPageObjectInner(objectData, resultScaleXY, animate)
            }
        }

        //We need to reset scale before animate page object
        if (scaleImageView.scale == scaleImageView.minScale) {
            switchObject()
        } else {
            scaleImageView.animateScale(.0f)!!
                .withDuration(100L)
                .withOnAnimationEventListener(object :
                    SubsamplingScaleImageView.DefaultOnAnimationEventListener() {
                    override fun onComplete() {
                        //without it view will have scale != scaleImageView.minScale for some reason
                        //This will trigger OnStateChangedListener and call blow animation
                        scaleImageView.resetScaleAndCenter()

                        switchObject()
                    }
                }).start()
        }
    }

    /**
     * Hide object on the page
     */
    fun hidePageObject() {
        check(scaleImageView.isReady) { "Image is not ready" }

        startBlowingAnimation()
    }

    /**
     * Reset currently displayed page object
     */
    fun reset() {
        onHidingAnimEnd = null

        objectImageView.apply {
            ViewCompat.animate(this).cancel()

            isGone = true

            scaleX = .0f
            scaleY = scaleX
        }

        loadingTask?.dispose()

        //Clear object drawable to be sure release resources
        //(objectImageView.drawable as? BitmapDrawable)?.bitmap?.recycle()
        objectImageView.setImageDrawable(null)

        scaleImageView.resetScaleAndCenter()
    }

    /**
     * Is page image object currently visible or not
     */
    fun isPageObjectVisible() =
        objectImageView.isVisible && animationState != ObjectAnimState.HIDING

    private fun startBlowingAnimation() {
        if (objectImageView.isVisible && animationState != ObjectAnimState.HIDING) {
            ViewCompat.animate(objectImageView)
                .setDuration(150L)
                .setInterpolator(interpolator)
                .scaleX(objectImageView.scaleX * 2.0f)
                .scaleY(objectImageView.scaleY * 2.0f)
                .alpha(.0f)
                .withStartAction { animationState = ObjectAnimState.HIDING }
                .withEndAction {
                    animationState = ObjectAnimState.IDLE

                    loadingTask?.dispose()

                    objectImageView.apply {
                        isGone = true

                        alpha = 1.0f

                        scaleX = .0f
                        scaleY = scaleX
                    }

                    onHidingAnimEnd?.invoke()
                    onHidingAnimEnd = null
                }.start()
        }
    }

    private fun showPageObjectInner(
        objectData: SelectedPageObject,
        scaleXY: Float,
        animate: Boolean
    ) {
        //Retrieve bbox position on displayed comic book page
        objectData.bbox.toPageImgProjection(viewBbox)

        //Scale image bbox projection to get bbox position relative to displayed page
        viewBbox.transform(matrix.apply {
            setScale(
                objectData.bbox.width() / viewBbox.width(),
                objectData.bbox.height() / viewBbox.height(),
                viewBbox.centerX(),
                viewBbox.centerY()
            )
        })

        //save top-left corner position to place bbox properly in the center of drawn speech balloon on the image
        val projectedObjectLeft = viewBbox.left
        val projectedObjectTop = viewBbox.top

        // Calculate final scale value to prevent showing scaled balloons outside of the visible area
        val calculatedScale = scaleXY.coerceIn(.0f, maxScaleXY(objectData.bbox))

        //Here we get result bbox position after scaling
        viewBbox.transform(matrix.apply {
            setScale(
                calculatedScale,
                calculatedScale,
                viewBbox.centerX(),
                viewBbox.centerY()
            )
        })

        objectData.bbox.roundOut(srcRndBbox)

        objectImageView.apply {
            updateLayoutParams<ViewGroup.LayoutParams> {
                //set size to help display [error] drawable in case of error
                width = srcRndBbox.width()
                height = srcRndBbox.height()
            }

            // Set it to make view centered relative to it projection on displayed page
            translationX = projectedObjectLeft
            translationY = projectedObjectTop
        }

        loadingTask = imageLoader.loadPageObject(
            objectData.bookPath,
            objectData.pagePos,
            srcRndBbox,
            objectImageView,
            error
        ) {
            //Now we are ready to show balloon view
            if (animate) {
                baseAnimator.scaleX(calculatedScale)
                    .scaleY(calculatedScale)
                    .objectTranslationBy(viewBbox, true)
                    .withStartAction {
                        animationState = ObjectAnimState.SHOWING

                        objectImageView.isVisible = true
                    }
                    .withEndAction { animationState = ObjectAnimState.IDLE }
                    .start()
            } else {
                objectImageView.apply {
                    isVisible = true

                    scaleX = calculatedScale
                    scaleY = calculatedScale

                    translationX += fixBboxTranslationX(viewBbox, true)
                    translationY += fixBboxTranslationY(viewBbox, true)
                }
            }
        }
    }

    /**
     * @param bbox bounding box
     *
     * @return max possible X and Y scale for provided bounding box
     */
    private fun maxScaleXY(bbox: RectF) =
        minOf(scaleImageView.width / bbox.width(), scaleImageView.height / bbox.height())

    /**
     * Set object translation to prevent showing outside of visible area
     * @param bbox object bounding  box
     * @param show is balloon show or hide animation
     */
    private fun ViewPropertyAnimatorCompat.objectTranslationBy(
        bbox: RectF,
        show: Boolean
    ): ViewPropertyAnimatorCompat {
        translationXBy(fixBboxTranslationX(bbox, show))

        translationYBy(fixBboxTranslationY(bbox, show))

        return this
    }

    private fun fixBboxTranslationX(bbox: RectF, show: Boolean) =
        fixBboxTranslation(bbox.left, bbox.right, .0f, scaleImageView.width.toFloat(), show)

    private fun fixBboxTranslationY(bbox: RectF, show: Boolean) =
        fixBboxTranslation(bbox.top, bbox.bottom, .0f, scaleImageView.height.toFloat(), show)

    /**
     * Calculate translation fix to prevent showing object behind screen edges
     * @param bboxMinEdge object bbox minimum value
     * @param bboxMaxEdge object bbox maximum value
     * @param min screen minimum value
     * @param max screen maximum value
     * @param show is value calculates for show or hide object
     * @return destination which should be applied to object to prevent showing outside of visible area
     */
    private fun fixBboxTranslation(
        bboxMinEdge: Float,
        bboxMaxEdge: Float,
        min: Float,
        max: Float,
        show: Boolean
    ): Float {
        val sign = if (show) {
            -1
        } else {
            1
        }

        return when {
            bboxMinEdge < min -> {
                bboxMinEdge * sign
            }
            bboxMaxEdge > max -> {
                (bboxMaxEdge - max) * sign
            }
            else -> {
                //case there both left/top and right/bottom are outside of visible area is not possible by current logic
                .0f
            }
        }
    }

    /**
     * Map this rectangle to it displayed page projection
     */
    private fun RectF.toPageImgProjection(out: RectF) {
        val (vBboxLeft, vBboxTop) = scaleImageView.sourceToViewCoord(left, top, point)!!

        val (vBboxRight, vBboxBottom) = scaleImageView.sourceToViewCoord(right, bottom, point)!!

        out.set(vBboxLeft, vBboxTop, vBboxRight, vBboxBottom)
    }

    private enum class ObjectAnimState { IDLE, SHOWING, HIDING }
}