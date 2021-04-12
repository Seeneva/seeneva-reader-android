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

package app.seeneva.reader.logic.usecase.image

import android.net.Uri
import app.seeneva.reader.data.source.jni.NativeSource
import app.seeneva.reader.logic.entity.ComicEncodedImage
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