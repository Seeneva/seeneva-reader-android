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

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.*
import android.graphics.drawable.shapes.OvalShape
import android.util.AttributeSet
import android.view.Gravity
import android.view.MenuInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.ActionMenuView
import androidx.core.content.ContextCompat
import androidx.core.content.res.getColorOrThrow
import androidx.core.content.res.use
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnLayout
import androidx.core.widget.ImageViewCompat
import app.seeneva.reader.R
import app.seeneva.reader.extension.inflate
import app.seeneva.reader.logic.ComicListViewType
import app.seeneva.reader.logic.entity.ComicListItem
import app.seeneva.reader.logic.image.ImageLoader
import app.seeneva.reader.logic.image.ImageLoadingTask
import app.seeneva.reader.logic.image.coil.asImageSizeProvider
import app.seeneva.reader.logic.image.entity.DrawablePalette
import app.seeneva.reader.logic.image.target.ImageLoaderTarget
import com.google.android.material.card.MaterialCardView
import kotlin.math.roundToInt
import app.seeneva.reader.logic.image.target.ImageLoaderTarget.State as CoverState
import com.google.android.material.R as MaterialR

/**
 * Used to show comic book thumbnail on lists
 */
class ComicItemView : MaterialCardView, ImageLoaderTarget<DrawablePalette> {
    private val delegate: Delegate

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(
        context,
        attrs,
        MaterialR.attr.materialCardViewStyle
    )

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        delegate =
            context.obtainStyledAttributes(attrs, R.styleable.ComicItemView, defStyleAttr, 0)
                .use {
                    when (val viewType =
                        viewTypeFromXML(
                            it.getInteger(
                                R.styleable.ComicItemView_type,
                                ComicListViewType.GRID.xmlValue
                            )
                        )) {
                        ComicListViewType.GRID -> GridDelegate(this)
                        ComicListViewType.LIST -> ListDelegate(this)
                        else -> throw IllegalArgumentException("Unknown view type: $viewType")
                    }
                }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onDetach()
    }

    override fun onImageLoadStateChanged(state: CoverState<DrawablePalette>) {
        delegate.onImageLoadStateChanged(state)
    }

    fun setImageLoader(imageLoader: ImageLoader?) {
        delegate.imageLoader = imageLoader
    }

    /**
     * Show comic book in the view
     * @param comicBook comic book to show
     */
    fun showComicBook(comicBook: ComicListItem) {
        delegate.showComicBook(comicBook)
    }

    /**
     * On detach from window.
     * Call if on [androidx.recyclerview.widget.RecyclerView.Adapter.onViewDetachedFromWindow]
     */
    fun onDetach() {
        //I need to cancel image loading to prevent Coil crash
        // [coil.size.ViewSizeResolver] will try to resume continuation when it was already resumed
        delegate.onDetach()
    }

    fun setOnActionsClickListener(onActionsClickListener: OnActionsClickListener?) {
        delegate.onActionsClickListener = onActionsClickListener
    }

    /**
     * Check is provided point can be used to select this comic thumb
     * @param x x coordinate of the point
     * @param y y coordinate of the point
     * @return true if this point can be uses during selection
     */
    fun iSelectionZone(x: Float, y: Float) = delegate.iSelectionZone(x, y)


    private abstract class Delegate(
        private val root: ComicItemView,
        layoutId: Int,
    ) : ImageLoaderTarget<DrawablePalette> {
        init {
            root.inflate<MaterialCardView>(layoutId, true)
        }

        var onActionsClickListener: OnActionsClickListener? = null

        var imageLoader: ImageLoader? = null

        protected val context: Context
            get() = root.context

        private val menuView: ActionMenuView = root.findViewById(R.id.menu)
        private val titleView: TextView = root.findViewById(R.id.name)
        private val coverView: ImageView = root.findViewById(R.id.cover)
        private val completedIconView: ImageView = root.findViewById(R.id.completed_icon)

        protected val titleHeight
            get() = titleView.height

        protected val coverHeight
            get() = coverView.height

        protected val defaultTitleColor
            get() = titleView.textColors.defaultColor

        @ColorInt
        private val defaultBackgroundColor = ContextCompat.getColor(context, R.color.light_blue_800)

        @ColorInt
        private val defaultCorruptedColor = ContextCompat.getColor(context, R.color.red_800)

        private val menuInflater: MenuInflater
            get() = checkNotNull((context as? Activity)?.menuInflater) { "Can't get menuInflater from context $context" }

        private val completedShape = ShapeDrawable(OvalShape())

        /**
         * Thumbnail drawable
         *
         * Generated from code because it uses optional rounded corners
         */
        private val placeholder by lazy { placeholderDrawable(context) }

        private val overflowIcon: Drawable?

        private var comicDisplayType = ComicDisplayType.DEFAULT

        private var coverLoadingData: CoverLoadingData? = null

        init {
            menuView.popupTheme = R.style.AppTheme
            menuInflater.inflate(R.menu.comics_list_item, menuView.menu)

            menuView.setOnMenuItemClickListener {
                onActionsClickListener?.let { clickListener ->
                    when (it.itemId) {
                        R.id.remove -> Action.DELETE
                        R.id.info -> Action.INFO
                        R.id.rename -> Action.RENAME
                        R.id.mark_read -> Action.MARK_READ
                        else -> null
                    }?.let {
                        clickListener.onActionClick(it)

                        true
                    }
                } ?: false
            }

            overflowIcon = menuView.overflowIcon?.mutate()?.let { DrawableCompat.wrap(it) }
        }

        override fun onImageLoadStateChanged(state: CoverState<DrawablePalette>) {
            coverLoadingData = coverLoadingData?.copy(state = state)

            val cover: Drawable?
            //default color for text, controls, etc
            @ColorInt
            val controlColor: Int
            //default color for backgrounds
            @ColorInt
            val backgroundColor: Int

            when (state) {
                is CoverState.Success<DrawablePalette> -> {
                    val (resultCover, resultPalette) = state.result

                    when (comicDisplayType) {
                        ComicDisplayType.DEFAULT -> {
                            val swatch = resultPalette.darkVibrantSwatch
                                ?: resultPalette.swatches.maxByOrNull { it.population }

                            controlColor = swatch?.titleTextColor ?: Color.WHITE

                            backgroundColor = swatch?.rgb ?: defaultBackgroundColor
                        }

                        ComicDisplayType.CORRUPTED -> {
                            controlColor = defaultCorruptedColor
                            backgroundColor = Color.WHITE
                        }
                    }

                    cover = resultCover
                }

                is CoverState.WithPlaceholder -> {
                    cover = state.placeholder

                    when (comicDisplayType) {
                        ComicDisplayType.DEFAULT -> {
                            controlColor = Color.WHITE
                            backgroundColor = Color.TRANSPARENT
                        }

                        ComicDisplayType.CORRUPTED -> {
                            controlColor = defaultCorruptedColor
                            backgroundColor = Color.TRANSPARENT
                        }
                    }
                }

                else -> throw IllegalArgumentException("Unknown cover state $state")
            }

            coverView.setImageDrawable(cover)

            applyState(
                comicDisplayType,
                cover,
                applyControlColorAlpha(controlColor),
                applyBackgroundColorAlpha(backgroundColor)
            )
        }

        /**
         * @see [ComicItemView.iSelectionZone]
         */
        fun iSelectionZone(x: Float, y: Float) =
            !IntArray(2).also { menuView.getLocationOnScreen(it) }
                .let { (mx, my) ->
                    RectF(
                        mx.toFloat(),
                        my.toFloat(),
                        (mx + menuView.width).toFloat(),
                        (my + menuView.height).toFloat()
                    )
                }
                .contains(x, y)

        /**
         * Display comic book data and start cover retrieving
         */
        fun showComicBook(comicBook: ComicListItem) {
            comicDisplayType = ComicDisplayType(comicBook)

            titleView.text = comicBook.title

            menuView.menu.findItem(R.id.mark_read).also { readMenuItem ->
                if (comicBook.completed) {
                    readMenuItem.setTitle(R.string.comic_list_not_completed)
                    completedIconView.visibility = View.VISIBLE
                } else {
                    readMenuItem.setTitle(R.string.comic_list_completed)
                    completedIconView.visibility = View.GONE
                }
            }

            coverView.colorFilter = if (comicBook.corrupted) {
                ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(.0f) })
            } else {
                null
            }

            coverLoadingData?.also {
                //prevent image flickering
                //we do not want to load cover again if:
                // * it is the same comic book (by id) AND
                // * cover loading task disposed for some reason (cancel or result) but cover state already has result or placeholder OR
                // * cover loading task is still running
                if (it.id == comicBook.id && ((it.task?.isDisposed == true && it.state != CoverState.Clear) || it.task?.isDisposed == false)) {
                    return
                }
            }

            //cancel loading task and remove any data about previous loading
            coverLoadingData?.task?.dispose()
            coverLoadingData = null

            imageLoader?.pageThumbnail(
                comicBook.path,
                comicBook.coverPosition,
                root,
                placeholder,
                sizeProvider = coverView.asImageSizeProvider()
            )?.also { coverLoadingData = CoverLoadingData(comicBook.id, it) }
                ?: onImageLoadStateChanged(CoverState.Error(placeholder))
        }

        fun onDetach() {
            coverLoadingData?.task?.dispose()
        }

        /**
         * Apply states to the view
         * @param displayType current comic book display type
         * @param controlColor default color of buttons and text
         * @param backgroundColor default color for any background
         */
        protected abstract fun applyState(
            displayType: ComicDisplayType,
            cover: Drawable?,
            @ColorInt controlColor: Int,
            @ColorInt backgroundColor: Int
        )

        protected fun setCover(cover: Drawable?) {
            coverView.setImageDrawable(cover)
        }

        /**
         * Set tint to overflow menu if it present
         * @param color tint color
         */
        protected fun tintOverflowIcon(@ColorInt color: Int) {
            if (overflowIcon != null) {
                DrawableCompat.setTint(overflowIcon, color)
            }
        }

        /**
         * Tint title and its background
         * @param titleColor color of the title text
         */
        protected fun tintTitle(@ColorInt titleColor: Int) {
            titleView.setTextColor(titleColor)
        }

        /**
         * Set comic book complete state icon colors
         * @param tint icon tint color
         * @param backgroundColor icon background color
         */
        protected fun setCompletedIcon(@ColorInt tint: Int, @ColorInt backgroundColor: Int) {
            ImageViewCompat.setImageTintList(completedIconView, ColorStateList.valueOf(tint))

            completedIconView.doOnLayout {
                if (backgroundColor != Color.TRANSPARENT) {
                    completedShape.paint.shader =
                        completeIconShader(backgroundColor, completedIconView.width.toFloat())

                    if (it.background == null) {
                        it.background = completedShape
                    } else {
                        it.invalidate()
                    }
                } else {
                    it.background = null
                }
            }
        }

        /**
         * Describes current cover loading process
         * @param id comic book id
         * @param task cover loading task if it was started
         * @param state current cover loading state
         */
        private data class CoverLoadingData(
            val id: Long,
            val task: ImageLoadingTask? = null,
            val state: CoverState<DrawablePalette> = CoverState.Clear
        )

        /**
         * Displayed comic book type
         */
        protected enum class ComicDisplayType {
            /**
             * Typical comic book
             */
            DEFAULT,

            /**
             * Corrupted comic book
             */
            CORRUPTED;

            companion object {
                /**
                 * Create a new one
                 * @param book color type will be selected depending on it
                 */
                operator fun invoke(book: ComicListItem) =
                    if (book.corrupted) {
                        CORRUPTED
                    } else {
                        DEFAULT
                    }
            }
        }

        protected companion object {
            private const val CONTROL_ALPHA = 230
            private const val BACKGROUND_ALPHA = 220

            /**
             * Set default alpha for the control color
             */
            @ColorInt
            fun applyControlColorAlpha(@ColorInt color: Int): Int =
                applyColorAlpha(color, CONTROL_ALPHA)

            /**
             * Set default alpha for the background color
             */
            @ColorInt
            fun applyBackgroundColorAlpha(@ColorInt color: Int): Int =
                applyColorAlpha(color, BACKGROUND_ALPHA)

            @ColorInt
            private fun applyColorAlpha(@ColorInt color: Int, alpha: Int) =
                if (color == Color.TRANSPARENT) {
                    color
                } else {
                    ColorUtils.setAlphaComponent(color, alpha)
                }

            /**
             * @param color color of the shader
             * @param radius radius of the shader
             *
             * @return shader used for completed icon
             */
            private fun completeIconShader(@ColorInt color: Int, radius: Float): Shader {
                val halfRadius = radius * 0.5f

                return RadialGradient(
                    halfRadius,
                    halfRadius,
                    radius,
                    color,
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
        }
    }

    private class GridDelegate(root: ComicItemView) :
        Delegate(root, R.layout.widget_comic_grid_thumb) {

        override fun applyState(
            displayType: ComicDisplayType,
            cover: Drawable?,
            @ColorInt controlColor: Int,
            @ColorInt backgroundColor: Int
        ) {
            // We need to add gradient over book cover to make it title readable
            setCover(if (cover != null && backgroundColor != Color.TRANSPARENT) {
                val gradient = GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    intArrayOf(backgroundColor, Color.TRANSPARENT)
                )

                val scaledGradient = ScaleDrawable(gradient, Gravity.BOTTOM, -1.0f, 1.0f)
                    .also {
                        it.level =
                            ((10000.0f * titleHeight * 2.0f) / coverHeight.toFloat()).roundToInt()
                    }

                LayerDrawable(arrayOf(cover, scaledGradient))
            } else {
                cover
            })

            setCompletedIcon(controlColor, backgroundColor)

            tintOverflowIcon(controlColor)

            tintTitle(controlColor)
        }
    }

    private class ListDelegate(root: ComicItemView) :
        Delegate(root, R.layout.widget_comic_list_thumb) {
        override fun applyState(
            displayType: ComicDisplayType,
            cover: Drawable?,
            @ColorInt controlColor: Int,
            @ColorInt backgroundColor: Int
        ) {
            setCover(cover)

            setCompletedIcon(controlColor, backgroundColor)

            if (displayType == ComicDisplayType.DEFAULT) {
                defaultTitleColor
            } else {
                controlColor
            }.also {
                tintOverflowIcon(it)
                tintTitle(it)
            }
        }
    }

    interface OnActionsClickListener {
        fun onActionClick(action: Action)
    }

    enum class Action {
        DELETE, INFO, RENAME, MARK_READ
    }

    companion object {
        private fun viewTypeFromXML(value: Int) =
            ComicListViewType.values().find { it.xmlValue == value }
                ?: throw IllegalArgumentException("Unknown XML comic item view type: $value")

        private val ComicListViewType.xmlValue
            get() = when (this) {
                ComicListViewType.GRID -> 0
                ComicListViewType.LIST -> 1
            }

        /**
         * Comic book cover placeholder drawable
         *
         * Generated from code because it uses optional rounded corners
         *
         * @param context
         */
        fun placeholderDrawable(
            context: Context,
        ): Drawable {
            //Create drawable from code to support pre Lollipop devices

            val backgroundDrawable =
                context.theme.obtainStyledAttributes(intArrayOf(MaterialR.attr.colorPrimary))
                    .use { it.getColorOrThrow(0) }.toDrawable()

            val scaledIconDrawable =
                AppCompatResources.getDrawable(context, R.drawable.ic_whale_simple)!!
                    .let { icon ->
                        ScaleDrawable(icon, Gravity.CENTER, 0.5f, 0.7f).also { it.level = 1 }
                    }

            return LayerDrawable(arrayOf(backgroundDrawable, scaledIconDrawable))
        }
    }
}