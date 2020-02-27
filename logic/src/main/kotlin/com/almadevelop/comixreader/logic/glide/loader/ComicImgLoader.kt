package com.almadevelop.comixreader.logic.glide.loader

import android.net.Uri
import com.almadevelop.comixreader.data.entity.ComicImage
import com.almadevelop.comixreader.data.source.jni.NativeSource
import com.almadevelop.comixreader.logic.glide.model.ComicPageModel
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import kotlinx.coroutines.*
import org.koin.core.KoinComponent
import org.koin.core.inject

/**
 * Loader of the comics images
 */
internal class ComicImgLoader(private val sourceLazy: Lazy<NativeSource>) : ModelLoader<ComicPageModel, ComicImage> {
    override fun handles(model: ComicPageModel): Boolean {
        return true
    }

    override fun buildLoadData(
        model: ComicPageModel,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<ComicImage>? {
        return ModelLoader.LoadData(
            ObjectKey(ThumbnailKey(model.path)),
            ComicImgFetcher(model, resizeType(width, height), sourceLazy)
        )
    }

    private data class ThumbnailKey(val path: Uri)

    private class ComicImgFetcher(
        private val model: ComicPageModel,
        private val resizeType: NativeSource.ImageResize,
        sourceLazy: Lazy<NativeSource>
    ) : DataFetcher<ComicImage>, CoroutineScope {
        override val coroutineContext = Job() + CoroutineName(javaClass.simpleName)

        private val source by sourceLazy

        override fun getDataClass(): Class<ComicImage> {
            return ComicImage::class.java
        }

        override fun cleanup() {
            //do nothing
        }

        override fun getDataSource(): DataSource {
            return DataSource.LOCAL
        }

        override fun cancel() {
            (this as CoroutineScope).cancel()
        }

        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in ComicImage>) {
            runBlocking {
                try {
                    val comicImage = source.getImage(model.path, model.position, resizeType)

                    ensureActive()

                    callback.onDataReady(comicImage)
                } catch (t: Exception) {
                    if (t !is CancellationException) {
                        callback.onLoadFailed(t)
                    }
                }
            }
        }
    }

    class Factory : ModelLoaderFactory<ComicPageModel, ComicImage>, KoinComponent {
        private val sourceLazy = inject<NativeSource>()

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<ComicPageModel, ComicImage> {
            return ComicImgLoader(sourceLazy)
        }

        override fun teardown() {
            //do nothing
        }
    }

    companion object {
        @JvmStatic
        private fun resizeType(width: Int, height: Int): NativeSource.ImageResize {
            return if (width == Target.SIZE_ORIGINAL || height == Target.SIZE_ORIGINAL) {
                NativeSource.ImageResize.None
            } else {
                NativeSource.ImageResize.Thumbnail(width, height)
            }
        }
    }
}