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

package app.seeneva.reader.logic.entity

import android.graphics.RectF
import app.seeneva.reader.logic.entity.ml.ObjectClass
import app.seeneva.reader.logic.extension.asRTreeEntry
import app.seeneva.reader.logic.storage.EncodedComicPageBorrower
import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import java.io.Closeable

/**
 * Single comic book page with data (encoded image and founded ML objects)
 *
 * @param id page id
 * @param img encoded page image. Should be closed after usage
 * @param objects founded ML objects on the page
 */
data class ComicPageData(
    val id: Long,
    val img: EncodedComicPageBorrower,
    val objects: ComicPageObjectContainer,
) : Closeable {
    override fun close() {
        img.returnObject()
    }
}

/**
 * Container for comic book page objects which helps to find objects by coordinates
 * @param objects underlying collection of objects
 * @param direction read direction
 */
data class ComicPageObjectContainer(
    private val objects: List<ComicPageObject>,
    val direction: Direction
) : Collection<ComicPageObject> by objects {
    /**
     * Helper R-Tree to search objects by coordinates
     */
    private val objectsRTree = RTree.create(objects.map(ComicPageObject::asRTreeEntry))

    /**
     * Get object by [index]
     * @param index requested object index
     */
    operator fun get(index: Int): ComicPageObject = objects[index]

    /**
     * Get object by [index]
     * @param index requested object index
     */
    fun getOrNull(index: Int) = objects.getOrNull(index)

    /**
     * Find objects by provided point (x, y)
     * @param x X coordinate
     * @param y Y coordinate
     * @return [Sequence] of page objects which contains provided point if any
     */
    operator fun get(x: Float, y: Float): Sequence<ComicPageObject> =
        objectsRTree.search(Geometries.point(x, y))
            .asSequence()
            .map { it.value() }
}

/**
 * Single comic book page ML object
 *
 * @param id object id
 * @param classId object class id
 * @param bbox object's bounding box
 */
data class ComicPageObject(
    val id: Long,
    val classId: ObjectClass,
    val bbox: RectF,
)