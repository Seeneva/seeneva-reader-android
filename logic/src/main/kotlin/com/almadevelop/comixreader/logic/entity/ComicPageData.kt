package com.almadevelop.comixreader.logic.entity

import android.graphics.RectF
import com.almadevelop.comixreader.logic.entity.ml.ObjectClass
import com.almadevelop.comixreader.logic.extension.asRTreeEntry
import com.almadevelop.comixreader.logic.storage.EncodedComicPageBorrower
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