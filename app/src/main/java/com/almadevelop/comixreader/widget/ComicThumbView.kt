package com.almadevelop.comixreader.widget

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.*
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RectShape
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.RecyclerView
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.extension.inflate
import com.almadevelop.comixreader.logic.CornerRadius
import com.almadevelop.comixreader.logic.ImageLoader
import com.almadevelop.comixreader.logic.ImageLoaderTarget
import com.almadevelop.comixreader.logic.entity.ComicListItem
import com.almadevelop.comixreader.logic.glide.BitmapPalette
import com.google.android.material.card.MaterialCardView
import org.koin.core.KoinComponent
import org.koin.core.get

/**
 * Used to show comic book thumbnail on lists
 */
class ComicThumbView : MaterialCardView, ImageLoaderTarget<BitmapPalette> {
    private val delegate: Delegate

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        val typedArray =
            context.obtainStyledAttributes(attrs, R.styleable.ComicThumbView, defStyleAttr, 0)

        try {
            delegate = when (val viewType =
                typedArray.getInteger(R.styleable.ComicThumbView_comic_view_type, VIEW_TYPE_GRID)) {
                VIEW_TYPE_GRID -> GridDelegate(this)
                VIEW_TYPE_LIST -> ListDelegate(this)
                else -> throw IllegalArgumentException("Unknown view type: $viewType")
            }
        } finally {
            typedArray.recycle()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        if (parent !is RecyclerView) {
            //otherwise should be called from an adapter
            cancelImageLoading()
        }
    }

    override fun onImageStateChanged(drawable: Drawable?, state: ImageLoaderTarget.State) {
        delegate.onImageStateChanged(drawable, state)
    }

    override fun onImageLoaded(obj: BitmapPalette) {
        delegate.onImageLoaded(obj)
    }


    fun showComicBook(comicBook: ComicListItem) {
        delegate.showComicBook(comicBook)
    }

    fun cancelImageLoading() {
        delegate.cancelImageLoading()
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
        private val root: ComicThumbView,
        layoutId: Int
    ) : ImageLoaderTarget<BitmapPalette>, KoinComponent {
        var onActionsClickListener: OnActionsClickListener? = null

        /**
         * Default round corner radius
         */
        protected val defaultCornerRadius: Float
            get() = root.radius

        protected val context: Context
            get() = root.context

        /**
         * Thumbnail corners radius topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius
         */
        protected abstract val thumbCornersRadius: FloatArray

        protected val menuView by lazy<ActionMenuView> { root.findViewById(R.id.menu) }
        protected val titleView by lazy<TextView> { root.findViewById(R.id.name) }
        protected val coverView by lazy<ImageView> { root.findViewById(R.id.cover) }
        protected val completedIconView by lazy<ImageView> { root.findViewById(R.id.completed_icon) }

        private val menuInflater: MenuInflater
            get() = requireNotNull((context as? Activity)?.menuInflater) { "Can't get menuInflater from context $context" }

        private val completedShape by lazy { ShapeDrawable().also { it.shape = OvalShape() } }

        /**
         * Thumbnail drawable
         *
         * Generated from code because it uses optional rounded corners
         */
        private val thumbPlaceholder by lazy<Drawable> {
            val backgroundDrawable =
                newSolidColorDrawable(
                    ContextCompat.getColor(context, R.color.cerulean),
                    thumbCornersRadius
                )

            val iconDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_whale_simple)

            val scaledIconDrawable = ScaleDrawable(iconDrawable, Gravity.CENTER, 0.5f, 0.7f)
                .also { it.level = 1 }

            LayerDrawable(arrayOf(backgroundDrawable, scaledIconDrawable))
        }

        private val overflowIcon: Drawable?

        private val imageLoader = get<ImageLoader>()

        private var comicState: ComicState = DefaultComicState()

        init {
            root.inflate<MaterialCardView>(layoutId, true).also {
                it.preventCornerOverlap = false
                it.radius = it.resources.getDimension(R.dimen.comic_thumb_corner_radius)
                it.foreground = newForegroundDrawable()
            }

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

        override fun onImageStateChanged(drawable: Drawable?, state: ImageLoaderTarget.State) {
            coverView.setImageDrawable(drawable)

            applyColors(ComicState.ColorType.Loading)
        }

        override fun onImageLoaded(obj: BitmapPalette) {
            val (bitmap, palette) = obj

            coverView.setImageBitmap(bitmap)

            val resultSwatch = palette.darkVibrantSwatch
                ?: requireNotNull(palette.swatches.maxBy { it.population })
                { "Can't get proper swatch from comic book thumbnail palette" }

            applyColors(ComicState.ColorType.Loaded(resultSwatch))
        }

        fun cancelImageLoading() {
            imageLoader.cancel(root)
        }

        /**
         * @see [ComicThumbView.iSelectionZone]
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
            comicState = ComicState.get(context, comicBook)

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

            coverView.colorFilter = comicState.thumbnailColorFilter()

            imageLoader.pageThumbnail(
                comicBook.path,
                comicBook.coverPosition,
                root,
                thumbPlaceholder,
                if (shouldRoundThumb) {
                    val (topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius) = thumbCornersRadius
                    CornerRadius(
                        topLeftRadius,
                        topRightRadius,
                        bottomRightRadius,
                        bottomLeftRadius
                    )
                } else {
                    CornerRadius()
                }
            )
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
         * Apply provided colors
         * @param controlColor color of buttons and text
         * @param backgroundColor color for any background
         */
        protected open fun applyColors(@ColorInt controlColor: Int, @ColorInt backgroundColor: Int) {
            setCompletedIcon(controlColor, backgroundColor)
        }

        /**
         * Apply colors related to current [comicState]
         * @param colorType color type to apply
         */
        private fun applyColors(colorType: ComicState.ColorType) {
            applyColors(
                comicState.getControlColor(colorType),
                comicState.getBackgroundColor(colorType)
            )
        }

        /**
         * Set comic book complete state icon colors
         * @param tint icon tint color
         * @param backgroundColor icon background color
         */
        private fun setCompletedIcon(@ColorInt tint: Int, @ColorInt backgroundColor: Int) {
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
         * Create new foreground drawable with optional rounded corners
         * @param cornersRadius corner radius
         */
        private fun newForegroundDrawable(cornersRadius: FloatArray = thumbCornersRadius): Drawable {
            val (tl, tr, br, bl) = cornersRadius

            return newForegroundDrawable(context, tl, tr, br, bl)
        }

        /**
         * Create new solid color drawable with optional rounded corners
         * @param color color of the drawable
         * @param cornersRadius corner radius
         * @return solid color drawable
         */
        private fun newSolidColorDrawable(@ColorInt color: Int, cornersRadius: FloatArray = thumbCornersRadius): Drawable {
            val (tl, tr, br, bl) = cornersRadius

            return newSolidColorDrawable(color, tl, tr, br, bl)
        }

        private abstract class ComicState {
            /**
             * Color used by Title and etc.
             */
            @ColorInt
            abstract fun getControlColor(colorType: ColorType): Int

            /**
             * Color used by backgrounds
             */
            @ColorInt
            abstract fun getBackgroundColor(colorType: ColorType): Int

            abstract fun thumbnailColorFilter(): ColorFilter?

            sealed class ColorType {
                data class Loaded(val swatch: Palette.Swatch) : ColorType()
                object Loading : ColorType()
            }

            companion object {
                private const val CONTROL_ALPHA = 230
                private const val BACKGROUND_ALPHA = 190

                @ColorInt
                fun defaultControlColor(@ColorInt color: Int): Int =
                    ColorUtils.setAlphaComponent(color, CONTROL_ALPHA)

                @ColorInt
                fun defaultBackgroundColor(@ColorInt color: Int): Int =
                    ColorUtils.setAlphaComponent(color, BACKGROUND_ALPHA)

                fun get(context: Context, comic: ComicListItem): ComicState =
                    if (comic.corrupted) {
                        BrokenComicState(context)
                    } else {
                        DefaultComicState()
                    }
            }
        }

        private class DefaultComicState : ComicState() {
            override fun getControlColor(colorType: ColorType): Int =
                when (colorType) {
                    is ColorType.Loaded -> defaultControlColor(colorType.swatch.titleTextColor)
                    is ColorType.Loading -> Color.WHITE
                }

            override fun getBackgroundColor(colorType: ColorType): Int =
                when (colorType) {
                    is ColorType.Loaded -> defaultBackgroundColor(colorType.swatch.rgb)
                    is ColorType.Loading -> Color.TRANSPARENT
                }

            override fun thumbnailColorFilter(): ColorFilter? = null
        }

        private class BrokenComicState(private val context: Context) : ComicState() {
            override fun getControlColor(colorType: ColorType): Int =
                ContextCompat.getColor(context, R.color.red_800)

            override fun getBackgroundColor(colorType: ColorType): Int =
                when (colorType) {
                    is ColorType.Loaded -> defaultBackgroundColor(Color.WHITE)
                    is ColorType.Loading -> Color.TRANSPARENT
                }

            override fun thumbnailColorFilter(): ColorFilter? =
                //make it black and white
                ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(.0f) })
        }

        protected companion object {
            /**
             * If true then round corners should be set programmatically. Actual on pre Lollipop devices
             */
            val shouldRoundThumb: Boolean
                get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP

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

            /**
             * @param context
             * @param tl top-left corner radius
             * @param tr top-right corner radius
             * @param br bottom-right corner radius
             * @param bl bottom-left corner radius
             *
             * @return foreground drawable used above a thumbnail of a comic book. Visible only when a view is activated
             */
            private fun newForegroundDrawable(
                context: Context,
                tl: Float,
                tr: Float,
                br: Float,
                bl: Float
            ): Drawable {
                val foregroundColor =
                    context.theme.obtainStyledAttributes(intArrayOf(R.attr.colorControlActivated))
                        .use {
                            ColorUtils.setAlphaComponent(it.getColorOrThrow(0), 0x64)
                        }

                return StateListDrawable().also {
                    it.addState(
                        intArrayOf(android.R.attr.state_enabled, android.R.attr.state_activated),
                        newSolidColorDrawable(foregroundColor, tl, tr, br, bl)
                    )
                }
            }

            /**
             * @param color color of the drawable
             * @param tl top-left corner radius
             * @param tr top-right corner radius
             * @param br bottom-right corner radius
             * @param bl bottom-left corner radius
             *
             * @return drawable of solid color which covers the while comic book thumbnail view
             */
            private fun newSolidColorDrawable(
                @ColorInt color: Int,
                tl: Float,
                tr: Float,
                br: Float,
                bl: Float
            ): Drawable {
                return if (shouldRoundThumb) {
                    //There is some issues on Android 4.1
                    //I need add empty RectF() as inset argument into RoundRectShape constructor
                    //And ShapeDrawable.setPadding(Rect())
                    //https://github.com/facebook/fresco/issues/501

                    val shape =
                        RoundRectShape(floatArrayOf(tl, tl, tr, tr, br, br, bl, bl), RectF(), null)

                    ShapeDrawable(shape).also {
                        it.paint.color = color
                        it.setPadding(Rect())
                    }
                } else {
                    color.toDrawable()
                }
            }
        }
    }

    private class GridDelegate(root: ComicThumbView) :
        Delegate(root, R.layout.widget_comic_grid_thumb) {
        private val titleRootView by lazy<ViewGroup> { root.findViewById(R.id.nameRoot) }

        override val thumbCornersRadius: FloatArray
            get() = floatArrayOf(
                defaultCornerRadius,
                defaultCornerRadius,
                defaultCornerRadius,
                defaultCornerRadius
            )

        private val titleDrawable by lazy {
            ShapeDrawable().also {
                it.shape = if (shouldRoundThumb) {
                    val (_, _, br, bl) = thumbCornersRadius
                    //round only bottom corners
                    RoundRectShape(floatArrayOf(.0f, .0f, .0f, .0f, br, br, bl, bl), null, null)
                } else {
                    //CardView will round corners for us
                    RectShape()
                }
            }
        }

        override fun applyColors(@ColorInt controlColor: Int, @ColorInt backgroundColor: Int) {
            super.applyColors(controlColor, backgroundColor)

            tintOverflowIcon(controlColor)

            tintTitle(controlColor, backgroundColor)
        }

        /**
         * Tint title and its background
         * @param titleColor color of the title text
         * @param backgroundColor color of the title background
         */
        private fun tintTitle(@ColorInt titleColor: Int, @ColorInt backgroundColor: Int) {
            titleView.setTextColor(titleColor)

            //we need wait until the view size will be calculated
            titleRootView.doOnLayout {
                if (backgroundColor != Color.TRANSPARENT) {
                    //view full width and height
                    val width = titleRootView.width
                    val height = titleRootView.height

                    //height of all title text lines
                    val titleHeight = titleView.lineHeight * titleView.maxLines

                    //set gradient shader
                    titleDrawable.paint.shader =
                        newTitleShader(width, height, titleHeight, backgroundColor)

                    if (it.background == null) {
                        it.background = titleDrawable
                    } else {
                        it.invalidate()
                    }
                } else {
                    it.background = null
                }
            }
        }


        private companion object {
            /**
             * Create a new title background shader
             *
             * @param width max width of a shader
             * @param height max height of a shader
             * @param titleHeight min height of a shader
             * @param color start color of the gradient
             * @return a new shader used by title
             */
            private fun newTitleShader(
                width: Int,
                height: Int,
                titleHeight: Int,
                @ColorInt color: Int
            ): Shader {
                val halfWidth = width.toFloat() * 0.5f

                return LinearGradient(
                    halfWidth,
                    height.toFloat(),
                    halfWidth,
                    (height - titleHeight * 2).coerceAtLeast(0).toFloat(),
                    intArrayOf(color, Color.TRANSPARENT),
                    floatArrayOf(.5f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
        }
    }

    private class ListDelegate(root: ComicThumbView) :
        Delegate(root, R.layout.widget_comic_list_thumb) {
        override val thumbCornersRadius: FloatArray
            get() = floatArrayOf(defaultCornerRadius, .0f, .0f, defaultCornerRadius)
    }

    interface OnActionsClickListener {
        fun onActionClick(action: Action)
    }

    enum class Action {
        DELETE, INFO, RENAME, MARK_READ
    }

    private companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
    }
}