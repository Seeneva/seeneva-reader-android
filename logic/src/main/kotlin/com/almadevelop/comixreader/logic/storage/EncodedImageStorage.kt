package com.almadevelop.comixreader.logic.storage

import android.net.Uri
import com.almadevelop.comixreader.logic.entity.ComicEncodedImage
import com.almadevelop.comixreader.logic.usecase.image.GetEncodedPageUseCase

internal typealias EncodedComicPageStorage = ObjectStoragePickPoint<Key, ComicEncodedImage>
typealias EncodedComicPageBorrower = ObjectBorrower<ComicEncodedImage>

internal data class Key(val path: Uri, val pagePosition: Long)

internal class EncodedImageSource(
    private val encodedPageUseCase: GetEncodedPageUseCase
) : ObjectStorageImpl.Source<Key, ComicEncodedImage> {
    override suspend fun new(key: Key) =
        encodedPageUseCase.getEncodedPage(key.path, key.pagePosition)

    override fun key(obj: ComicEncodedImage) =
        Key(obj.path, obj.position)

    override suspend fun onReleased(obj: ComicEncodedImage) {
        super.onReleased(obj)

        obj.inner.close()
    }
}

/**
 * Borrow encoded comic book page by provided params
 * @param path comic book path
 * @param pagePosition page position
 */
internal suspend fun EncodedComicPageStorage.borrowEncodedComicPage(path: Uri, pagePosition: Long) =
    borrow(Key(path, pagePosition))