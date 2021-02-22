package com.almadevelop.comixreader.logic.usecase

import android.graphics.RectF
import com.almadevelop.comixreader.common.coroutines.Dispatched
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookPageSource
import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookSource
import com.almadevelop.comixreader.logic.comic.generateReadOrderedObjects
import com.almadevelop.comixreader.logic.entity.ComicPageData
import com.almadevelop.comixreader.logic.entity.ComicPageObject
import com.almadevelop.comixreader.logic.entity.ComicPageObjectContainer
import com.almadevelop.comixreader.logic.entity.Direction
import com.almadevelop.comixreader.logic.entity.ml.ObjectClass
import com.almadevelop.comixreader.logic.extension.objectsDataById
import com.almadevelop.comixreader.logic.storage.EncodedComicPageStorage
import com.almadevelop.comixreader.logic.storage.borrowEncodedComicPage
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