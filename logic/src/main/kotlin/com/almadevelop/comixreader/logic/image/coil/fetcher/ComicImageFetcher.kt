package com.almadevelop.comixreader.logic.image.coil.fetcher

import android.content.Context
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import coil.bitmap.BitmapPool
import coil.decode.DataSource
import coil.decode.Options
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.size.OriginalSize
import coil.size.PixelSize
import coil.size.Size
import com.almadevelop.comixreader.logic.entity.ComicEncodedImage
import com.almadevelop.comixreader.logic.extension.allowFastResizing
import com.almadevelop.comixreader.logic.extension.forcedBitmapConfig
import com.almadevelop.comixreader.logic.image.BitmapDiskCache
import com.almadevelop.comixreader.logic.image.coil.data.ComicPageFetcherData
import com.almadevelop.comixreader.logic.storage.EncodedComicPageStorage
import com.almadevelop.comixreader.logic.storage.borrowEncodedComicPage
import com.almadevelop.comixreader.logic.storage.use
import com.almadevelop.comixreader.logic.usecase.image.DecodePageUseCase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer
import org.tinylog.kotlin.Logger

internal class ComicImageFetcher(
    context: Context,
    private val encodedComicPageStorage: EncodedComicPageStorage,
    private val useCase: DecodePageUseCase,
    private val cache: BitmapDiskCache
) : Fetcher<ComicPageFetcherData> {
    private val context = context.applicationContext

    override suspend fun fetch(
        pool: BitmapPool,
        data: ComicPageFetcherData,
        size: Size,
        options: Options
    ): FetchResult {
        val cacheKey = data.asCacheKey(size)

        // Give ability to force which bitmap config I want dto use
        // Without it Coil will always use ARGB_8888.
        // See RequestService isConfigValidForHardwareAllocation
        val config = options.forcedBitmapConfig ?: options.config

        // get bitmap from the cache or open comic book container
        val result = if (options.diskCachePolicy.readEnabled) {
            Logger.debug("Trying to get image from disk cache")
            cache.get(cacheKey, config)
        } else {
            null
        } ?: data.encode { encodedPage ->
            Logger.debug("Start image encoding")

            val (bitmap, pooled) = when (size) {
                is OriginalSize -> {
                    val width: Int
                    val height: Int

                    //Use crop size if we has it
                    //Use image size otherwise
                    if (data.region != null) {
                        width = data.region.width()
                        height = data.region.height()
                    } else {
                        width = encodedPage.width
                        height = encodedPage.height
                    }

                    PixelSize(width, height)
                }
                is PixelSize -> size
            }.let { (width, height) ->
                when (val bitmap = pool.getDirtyOrNull(width, height, config)) {
                    null -> createBitmap(width, height, config) to false
                    else -> bitmap to true
                }
            }

            Logger.debug("Bitmap was allocated using bitmap pool: $pooled")

            try {
                currentCoroutineContext().ensureActive()

                useCase.decodePageIntoBitmap(encodedPage, bitmap, data.region, options.allowFastResizing)

                //save to the cache if we allowed to
                if (options.diskCachePolicy.writeEnabled) {
                    cache.put(cacheKey, bitmap)
                }
            } catch (t: Throwable) {
                // synchronize needed to prevent race condition between Kotlin and Native part
                // Bitmap.recycle can be called after AndroidBitmap_lockPixels but before AndroidBitmap_unlockPixels
                // In that case NDK will throw ANDROID_BITMAP_RESULT_JNI_EXCEPTION after AndroidBitmap_unlockPixels was called
                synchronized(bitmap) {
                    // in case of any Error (e.g. Cancellation) clear underlying bitmap
                    pool.put(bitmap)
                }

                throw t
            }

            // Comic book pages shouldn't contain alpha channel
            bitmap.setHasAlpha(false)

            bitmap
        }

        return DrawableResult(
            result.toDrawable(context.resources),
            size !is OriginalSize,
            DataSource.DISK
        )
    }

    override fun key(data: ComicPageFetcherData) = data.asCacheKey()

    /**
     * Encode the data image using [EncodedComicPageStorage] and invoke provided function
     * @param body function to invoke on result encoded image
     * @return result of the [body]
     */
    private suspend inline fun <R> ComicPageFetcherData.encode(body: (ComicEncodedImage) -> R): R =
        encodedComicPageStorage.borrowEncodedComicPage(path, pagePosition).use(body)

    /**
     * Represent provided data as cache key
     * @param size target size
     */
    private fun ComicPageFetcherData.asCacheKey(size: Size? = null) =
        Buffer()
            .writeUtf8(path.toString())
            .writeLong(pagePosition)
            .also {
                if (size != null) {
                    val width: Int
                    val height: Int

                    when (size) {
                        is OriginalSize -> {
                            width = 0
                            height = 0
                        }
                        is PixelSize -> {
                            width = size.width
                            height = size.height
                        }
                    }

                    it.writeInt(width).writeInt(height)
                }

                if (region != null) {
                    it.writeInt(region.left)
                        .writeInt(region.top)
                        .writeInt(region.right)
                        .writeInt(region.bottom)
                }
            }
            .use { it.md5().hex() }
}