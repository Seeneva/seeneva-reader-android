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

package app.seeneva.reader.logic.usecase

import android.graphics.RectF
import app.seeneva.reader.common.coroutines.Dispatched
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.data.source.local.db.dao.ComicBookPageSource
import app.seeneva.reader.data.source.local.db.dao.ComicBookSource
import app.seeneva.reader.logic.comic.generateReadOrderedObjects
import app.seeneva.reader.logic.entity.ComicPageData
import app.seeneva.reader.logic.entity.ComicPageObject
import app.seeneva.reader.logic.entity.ComicPageObjectContainer
import app.seeneva.reader.logic.entity.Direction
import app.seeneva.reader.logic.entity.ml.ObjectClass
import app.seeneva.reader.logic.extension.objectsDataById
import app.seeneva.reader.logic.storage.EncodedComicPageStorage
import app.seeneva.reader.logic.storage.borrowEncodedComicPage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*

interface GetPageDataUseCase {
    /**
     * Get comic book page data (encoded page image and founded ML objects)
     *
     * @param pageId comic book page id
     * @throws IllegalStateException in case if there is no such comic book page
     */
    fun subscribePageData(pageId: Long): Flow<ComicPageData>
}

internal class GetPageDataUseCaseImpl(
    private val encodedPageStorage: EncodedComicPageStorage,
    private val localPageSource: ComicBookPageSource,
    private val bookSource: ComicBookSource,
    override val dispatchers: Dispatchers
) : GetPageDataUseCase, Dispatched {
    override fun subscribePageData(pageId: Long) =
        flow {
            // get page's objects data
            val pageObjectsData =
                checkNotNull(
                    localPageSource.objectsDataById(
                        pageId,
                        setOf(ObjectClass.PANEL, ObjectClass.SPEECH_BALLOON)
                    )
                ) { "Can't get page's $pageId ML objects" }

            currentCoroutineContext().ensureActive()

            val pageEncodedImage = encodedPageStorage.borrowEncodedComicPage(
                pageObjectsData.bookPath,
                pageObjectsData.position
            )

            try {
                currentCoroutineContext().ensureActive()

                // page image dimensions
                val pageWidth: Int
                val pageHeight: Int

                pageEncodedImage.borrowedObject().apply {
                    pageWidth = width
                    pageHeight = height
                }

                val pageObjects = pageObjectsData.objects
                    .map {
                        ComicPageObject(
                            it.id,
                            ObjectClass.requireFromId(it.classId),
                            RectF(
                                (it.xMin * pageWidth),
                                (it.yMin * pageHeight),
                                (it.xMax * pageWidth),
                                (it.yMax * pageHeight)
                            )
                        )
                    }

                emitAll(bookSource.subscribeOnDirection(pageObjectsData.bookId)
                    .distinctUntilChanged()
                    .map(Direction::fromId)
                    .map {
                        ComicPageData(
                            pageId,
                            pageEncodedImage,
                            ComicPageObjectContainer(
                                generateReadOrderedObjects(
                                    pageObjects,
                                    pageWidth,
                                    pageHeight,
                                    it
                                ),
                                it
                            )
                        )
                    })
            } catch (t: Throwable) {
                pageEncodedImage.returnObject()

                throw t
            }
        }.flowOn(dispatchers.io)
}