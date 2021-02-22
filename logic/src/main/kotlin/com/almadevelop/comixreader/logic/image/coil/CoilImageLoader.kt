package com.almadevelop.comixreader.logic.image.coil

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.core.app.ActivityManagerCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import coil.request.*
import coil.size.OriginalSize
import coil.size.Precision
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.extension.allowFastResizing
import com.almadevelop.comixreader.logic.extension.forceBitmapConfig
import com.almadevelop.comixreader.logic.image.*
import com.almadevelop.comixreader.logic.image.coil.data.ComicPageFetcherData
import com.almadevelop.comixreader.logic.image.coil.target.ViewPaletteTarget
import com.almadevelop.comixreader.logic.image.entity.DrawablePalette
import com.almadevelop.comixreader.logic.image.target.ImageLoaderTarget
import org.tinylog.kotlin.Logger
import coil.ImageLoader as CoilImageLoaderInner

internal class CoilImageLoader(
    context: Context,
    private val imageLoader: CoilImageLoaderInner,
    private val dispatchers: Dispatchers,
    private val lifecycle: Lifecycle,
) : ImageLoader {
    private val context = context.applicationContext

    private val activityManager by lazy { context.getSystemService<ActivityManager>()!! }

    override fun <T> pageThumbnail(
        comicBookPath: Uri,
        pagePosition: Long,
        target: T,
        placeholder: Drawable?,
        cornerRadius: CornerRadius,
        sizeProvider: ImageSizeProvider
    ): ImageLoadingTask where T : View, T : ImageLoaderTarget<DrawablePalette> {
        return imageLoader.enqueue {
            data(ComicPageFetcherData.thumb(comicBookPath, pagePosition))
            placeholder(placeholder)
            error(placeholder)
            scale(Scale.FILL)
            setDeviceOptimizedParams()
            crossfade(true)

            size(sizeProvider.asSizeResolver())

            transformations(
                RoundedCornersTransformation(
                    cornerRadius.topLeft,
                    cornerRadius.topRight,
                    cornerRadius.bottomLeft,
                    cornerRadius.bottomRight
                )
            )

            target(ViewPaletteTarget(target, dispatchers))
        }.asTask()
    }

    override fun viewerPreview(
        comicBookPath: Uri,
        pagePosition: Long,
        target: ImageView,
        placeholder: Drawable?
    ) = imageLoader.enqueue {
        data(ComicPageFetcherData.thumb(comicBookPath, pagePosition))
        placeholder(placeholder)
        error(placeholder)
        scale(Scale.FILL)
        allowRgb565(true)
        crossfade(true)

        forceBitmapConfig(Bitmap.Config.RGB_565)
        allowFastResizing()

        target(target)
    }.asTask()

    override suspend fun decodeRegion(
        comicBookPath: Uri,
        pagePosition: Long,
        cropRegion: Rect?,
        size: ImageSize
    ): Bitmap = imageLoader.execute {
        data(ComicPageFetcherData.region(comicBookPath, pagePosition, cropRegion))
        diskCachePolicy(CachePolicy.DISABLED)
        memoryCachePolicy(CachePolicy.DISABLED)
        setDeviceOptimizedParams()
        precision(Precision.EXACT)
        size(size.innerSize)
    }.asSuccessDrawable<BitmapDrawable>().bitmap

    override fun loadPageObject(
        comicBookPath: Uri,
        pagePosition: Long,
        bbox: Rect,
        target: ImageView?,
        error: Drawable?,
        onFinished: () -> Unit
    ) = imageLoader.enqueue {
        loadPageObjectBaseRequest(comicBookPath, pagePosition, bbox)

        crossfade(false)

        //if target is null than it is a prefetch image task
        if (target != null) {
            target(target)
        }

        error(error)

        listener(
            onSuccess = { _, _ -> onFinished() },
            onError = { _, t ->
                Logger.error(t, "Can't load page object image")
                onFinished()
            })
    }.asTask()

    override suspend fun loadPageObjectBitmap(
        comicBookPath: Uri,
        pagePosition: Long,
        bbox: Rect,
    ): Bitmap = imageLoader.execute {
        loadPageObjectBaseRequest(comicBookPath, pagePosition, bbox)
    }.asSuccessDrawable<BitmapDrawable>().bitmap

    /**
     * Base request params for load comic book page object image
     * @see loadPageObjectBitmap
     */
    private fun ImageRequest.Builder.loadPageObjectBaseRequest(
        comicBookPath: Uri,
        pagePosition: Long,
        bbox: Rect,
        config: Bitmap.Config = Bitmap.Config.RGB_565
    ) {
        data(ComicPageFetcherData.region(comicBookPath, pagePosition, bbox))

        diskCachePolicy(CachePolicy.ENABLED)
        memoryCachePolicy(CachePolicy.ENABLED)

        precision(Precision.EXACT)
        size(OriginalSize)

        forceBitmapConfig(config)

        allowRgb565(true)
    }

    private inline fun CoilImageLoaderInner.enqueue(body: ImageRequest.Builder.() -> Unit = {}): Disposable =
        enqueue(request(body))

    private suspend inline fun CoilImageLoaderInner.execute(body: ImageRequest.Builder.() -> Unit = {}): ImageResult =
        execute(request(body))

    private inline fun request(body: ImageRequest.Builder.() -> Unit = {}) =
        ImageRequest.Builder(context).lifecycle(lifecycle).apply(body).build()

    /**
     * Extract success drawable and throws exception in case of any error
     */
    private inline fun <reified T : Drawable> ImageResult.asSuccessDrawable(): T =
        when (this) {
            is SuccessResult -> (drawable as? T
                ?: throw ClassCastException("Can't cast Coil result drawable to ${T::class.java.simpleName} from loaded result"))
            is ErrorResult -> throw throwable
        }

    /**
     * Apply optimization depends on device memory specifications
     */
    private fun ImageRequest.Builder.setDeviceOptimizedParams() {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.O && activityManager.memoryClass <= 48) ||
            ActivityManagerCompat.isLowRamDevice(activityManager)
        ) {
            allowRgb565(true)

            forceBitmapConfig(Bitmap.Config.RGB_565)
        } else {
            forceBitmapConfig(Bitmap.Config.ARGB_8888)
        }
    }
}