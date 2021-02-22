package com.almadevelop.comixreader.screen.viewer.page

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityManagerCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import com.almadevelop.comixreader.extension.SubsamplingImageEvent
import com.almadevelop.comixreader.extension.imageEventsFlow
import com.almadevelop.comixreader.logic.image.ImageLoader
import com.almadevelop.comixreader.logic.image.ImageSize
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.ImageViewState
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.decoder.DecoderFactory
import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.tinylog.kotlin.Logger
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Wrapper around [SubsamplingScaleImageView] to help set and use custom decoders
 */
class PageViewer(
    private val scaleView: SubsamplingScaleImageView,
    private val imageLoader: ImageLoader,
    lifecycle: Lifecycle
) : DefaultLifecycleObserver {
    private val context
        get() = scaleView.context

    private val activityManager by lazy { context.getSystemService<ActivityManager>()!! }
    private val runtime by lazy { Runtime.getRuntime() }

    private val _imageEventsStateFlow = MutableStateFlow<PageEvent?>(null)

    val imageEventsStateFlow = _imageEventsStateFlow.asStateFlow()

    /**
     * Scope for all running comic book page decoders
     * Will be cancelled as soon as [Lifecycle] coroutine scope cancelled
     */
    private val decoderRootScope = Job(lifecycle.coroutineScope.coroutineContext[Job])
        .also {
            it.invokeOnCompletion {
                // I call it here and not on `onDestroy` to prevent ANR in case if you swipe pages really fast
                scaleView.recycle()
            }
        }

    init {
        _imageEventsStateFlow.subscriptionCount
            .map { it > 0 }
            .distinctUntilChanged()
            .flatMapLatest { active ->
                if (active) {
                    scaleView.imageEventsFlow()
                        .transform {
                            when (it) {
                                is SubsamplingImageEvent.Loaded -> {
                                    emit(PageEvent.Loaded)
                                }
                                is SubsamplingImageEvent.ImageLoadError -> {
                                    Logger.error(it.e, "Comic book page viewer error")
                                    emit(PageEvent.Error(it.e))
                                }
                                is SubsamplingImageEvent.TileLoadError -> {
                                    Logger.error(it.e, "Comic book page viewer tile error")
                                }
                                else -> {
                                    // ignore other events
                                }
                            }
                        }
                } else {
                    emptyFlow()
                }
            }
            .onEach { _imageEventsStateFlow.value = it }
            .launchIn(lifecycle.coroutineScope)

        scaleView.setExecutor(Dispatchers.IO.asExecutor())

        lifecycle.addObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        decoderRootScope.cancelChildren()
    }

    fun showPage(pageSrc: PageSrc, state: ImageViewState? = null) {
        //cancel all decoders task
        decoderRootScope.cancelChildren()

        if (scaleView.hasImage()) {
            scaleView.recycle()
        }

        scaleView.setBitmapDecoderFactory(
            PageDecoderFactory(
                pageSrc,
                imageLoader,
                decoderRootScope
            )
        )
        scaleView.setRegionDecoderFactory(
            PageRegionDecoderFactory(
                pageSrc,
                imageLoader,
                decoderRootScope
            )
        )

        setImage(pageSrc, state)
    }

    private fun setImage(page: PageSrc, state: ImageViewState? = null) {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.O && activityManager.memoryClass <= 48) ||
            ActivityManagerCompat.isLowRamDevice(activityManager)
        ) {
            scaleView.setEagerLoadingEnabled(false)

            // Decrease tiles dpi on devices with low memory or without bitmap native heap support

            // Calculate available heap size
            val availHeapSize = runtime.run {
                val usedMem = totalMemory() - freeMemory()
                val maxHeapSize = maxMemory()

                // Available heap size shouldn't be greater than device memory class
                min(maxHeapSize - usedMem, activityManager.memoryClass * 1024L * 1024L)
            }

            val pageSizeARGB = page.size

            val densityFactor = if (availHeapSize <= 0 || pageSizeARGB < availHeapSize) {
                // If for some reason available heap size is negative
                // Or decoded page size is less than available size
                3
            } else {
                // I hope there is no real device there this case is possible :)
                (pageSizeARGB / (availHeapSize * 0.5f)).roundToInt()
            }

            val minDpi = context.resources.displayMetrics.densityDpi / densityFactor

            Logger.debug("Viewer min dpi: $minDpi")

            scaleView.setMinimumTileDpi(minDpi)
        }

        _imageEventsStateFlow.value = PageEvent.Loading(page.path, page.pagePosition)

        scaleView.setImage(
            ImageSource.uri(Uri.EMPTY)
                .dimensions(page.width, page.height)
                .tilingEnabled(),
            state
        )
    }

    /**
     * Size of the page in bytes (32 bit depth)
     */
    private val PageSrc.size: Long
        get() = width * height * 4L

    /**
     * Helper to run scoped decode tasks
     */
    private abstract class BasePageDecoderFactory<T>(
        private val pageSrc: PageSrc,
        private val imageLoader: ImageLoader,
        parent: Job?
    ) : DecoderFactory<T> {
        private val scope = Job(parent)

        override fun make(): T {
            // cancel all decoded tasks before create new decoder
            scope.cancelChildren()

            return makeScoped(pageSrc, imageLoader, scope)
        }

        /**
         * Create coroutine scoped decoder
         * @param pageSrc source comic book page
         * @param imageLoader image loader instance
         * @param scope parent scope
         */
        protected abstract fun makeScoped(
            pageSrc: PageSrc,
            imageLoader: ImageLoader,
            scope: Job
        ): T
    }

    private class PageDecoderFactory(
        pageSrc: PageSrc,
        imageLoader: ImageLoader,
        parent: Job?
    ) : BasePageDecoderFactory<ImageDecoder>(pageSrc, imageLoader, parent) {

        override fun makeScoped(
            pageSrc: PageSrc,
            imageLoader: ImageLoader,
            scope: Job
        ) = PageDecoder(pageSrc.path, pageSrc.pagePosition, imageLoader, scope)

        private class PageDecoder(
            private val path: Uri,
            private val pagePosition: Long,
            private val imageLoader: ImageLoader,
            scope: Job
        ) : ImageDecoder {
            private val coroutineScope = CoroutineScope(Dispatchers.Unconfined + Job(scope))

            override fun decode(context: Context, uri: Uri): Bitmap =
                runBlocking { decodeAsync().await() }

            private fun decodeAsync() =
                coroutineScope.async { imageLoader.decodeRegion(path, pagePosition) }
        }
    }

    private class PageRegionDecoderFactory(
        pageSrc: PageSrc,
        imageLoader: ImageLoader,
        parent: Job?
    ) : BasePageDecoderFactory<ImageRegionDecoder>(pageSrc, imageLoader, parent) {
        override fun makeScoped(
            pageSrc: PageSrc,
            imageLoader: ImageLoader,
            scope: Job
        ) = PageRegionDecoder(pageSrc, imageLoader, scope)

        private class PageRegionDecoder(
            private val pageSrc: PageSrc,
            private val imageLoader: ImageLoader,
            scope: Job
        ) : ImageRegionDecoder {
            private val coroutineScope = CoroutineScope(Dispatchers.Unconfined + Job(scope))

            @Volatile
            private var ready = true

            override fun isReady() = ready

            override fun init(context: Context, uri: Uri): Point =
                pageSrc.let { Point(it.width, it.height) }

            override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap =
                runBlocking { decodeRegionAsync(sRect, sampleSize).await() }

            override fun recycle() {
                coroutineScope.coroutineContext.cancelChildren()

                ready = false
            }

            private fun decodeRegionAsync(sRect: Rect, sampleSize: Int) =
                coroutineScope.async {
                    imageLoader.decodeRegion(
                        pageSrc.path,
                        pageSrc.pagePosition,
                        sRect,
                        ImageSize.specific(sRect.width() / sampleSize, sRect.height() / sampleSize)
                    )
                }
        }
    }

    data class PageSrc(val path: Uri, val pagePosition: Long, val width: Int, val height: Int)

    sealed class PageEvent {
        object Loaded : PageEvent()
        data class Loading(val path: Uri, val position: Long) : PageEvent()
        data class Error(val e: Exception) : PageEvent()
    }
}