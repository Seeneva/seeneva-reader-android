/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
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

package app.seeneva.reader.logic.storage

import android.net.Uri
import app.seeneva.reader.logic.entity.ComicEncodedImage
import app.seeneva.reader.logic.usecase.image.GetEncodedPageUseCase

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