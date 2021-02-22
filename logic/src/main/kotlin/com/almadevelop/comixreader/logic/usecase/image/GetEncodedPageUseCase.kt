package com.almadevelop.comixreader.logic.usecase.image

import android.net.Uri
import com.almadevelop.comixreader.data.source.jni.NativeSource
import com.almadevelop.comixreader.logic.entity.ComicEncodedImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal interface GetEncodedPageUseCase {
    /**
     * Get encoded image by specific position
     *
     * @param path path to comic book which should be open
     * @param position image position to open
     * @return encoded comic book image
     */
    suspend fun getEncodedPage(path: Uri, position: Long): ComicEncodedImage
}

internal class GetEncodedPageUseCaseImpl(
    private val nativeSource: NativeSource
) : GetEncodedPageUseCase {
    override suspend fun getEncodedPage(path: Uri, position: Long): ComicEncodedImage {
        val pageImageData = nativeSource.getPageImageData(path, position)

        return try {
            //Just to be sure if coroutine was cancelled
            currentCoroutineContext().ensureActive()

            ComicEncodedImage(
                path,
                position,
                pageImageData
            )
        } catch (e: CancellationException) {
            pageImageData.close()
            throw e
        }
    }
}