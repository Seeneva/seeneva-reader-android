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

package app.seeneva.reader.logic.comic

import android.graphics.RectF
import app.seeneva.reader.logic.entity.ComicPageObject
import app.seeneva.reader.logic.entity.Direction
import app.seeneva.reader.logic.entity.ml.ObjectClass
import app.seeneva.reader.logic.extension.asRTreeEntry
import app.seeneva.reader.logic.extension.asRTreeGeometry
import app.seeneva.reader.logic.extension.component1
import app.seeneva.reader.logic.extension.component2
import com.github.davidmoten.rtree2.Entry
import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Rectangle
import java.util.EnumMap
import java.util.TreeMap
import kotlin.math.absoluteValue
import kotlin.math.min

//TODO maybe it is best to give user a chance to tweak it somehow?
/**
 * Required minimal panels edge difference
 */
private const val PANEL_MIN_DIFF = 160.0f

/**
 * Required minimal object group edge difference
 */
private const val GROUP_MIN_DIFF = 80.0f

private const val OBJECT_NEIGHBOUR_MIN_DIFF = 20.0f

/**
 * Average min difference that should be between object edges to consider them different
 */
private const val OBJECT_MIN_DIFF = 15.0f

/**
 * How much object X coordinate should be hit to consider it beneath another object (in percent)
 */
private const val OBJECT_BENEATH = 0.15f

/**
 * Sort provided ML comic book objects founded on the page
 * @param objects founded ML object on the page
 * @param pageWidth current page width
 * @param pageHeight current page height
 * @param direction book read direction
 */
internal fun generateReadOrderedObjects(
    objects: List<ComicPageObject>,
    pageWidth: Int,
    pageHeight: Int,
    direction: Direction
): List<ComicPageObject> {
    // Check if page has any objects
    if (objects.isEmpty()) {
        return emptyList()
    }

    // group ML objects by its class and convert into view layer object
    val objectsByClass = objects.groupByTo(EnumMap(ObjectClass::class.java)) { it.classId }

    // Check if page has any objects on it except for comic book panels
    if ((objectsByClass.keys - ObjectClass.PANEL).isEmpty()) {
        return emptyList()
    }

    //All objects R-Tree
    val objectsRTree = RTree.create(objects.map(ComicPageObject::asRTreeEntry))

    //Reusable map for intersected objects
    val intersectedObjects =
        EnumMap<ObjectClass, MutableList<Entry<ComicPageObject, Rectangle>>>(ObjectClass::class.java)

    // Map of comic book page panels with TreeSet of objects inside it
    val objectsByPanel =
        TreeMap<ComicPageObject, MutableList<PanelGroup>> { o1, o2 ->
            defaultComparator(
                direction,
                diffProportionally(
                    pageWidth,
                    pageHeight,
                    PANEL_MIN_DIFF
                )
            ).compare(o1.bbox, o2.bbox)
        }

    //Already consumed objects ids
    val consumedObjectIds = hashSetOf<Long>()

    objectsByClass
        .asSequence()
        .filterNot { it.key == ObjectClass.PANEL }
        .flatMap { it.value }
        .filterNot { consumedObjectIds.contains(it.id) }
        .forEach { obj ->
            intersectedObjects.clear()

            val objGeometry = obj.bbox.asRTreeGeometry()

            // Panel for this object neighbours. Can be fake panel
            val panel: ComicPageObject

            val group: PanelGroup

            //Found any intersections with single page object [obj]
            objectsRTree.search(objGeometry)
                .groupByTo(intersectedObjects) { it.value().classId }
                .also { _intersectedObjects ->

                    //get parent panel which has the most intersection area
                    val parentPanel = _intersectedObjects[ObjectClass.PANEL]?.maxByOrNull {
                        it.geometry().intersectionArea(objGeometry)
                    }?.value()

                    //All neighbours
                    val neighbourObjs = arrayListOf<ComicPageObject>()

                    val neighbourMBR = RectF()

                    _intersectedObjects.asSequence()
                        .filterNot { it.key == ObjectClass.PANEL }
                        .flatMap { it.value }
                        .flatMap { (o, g) ->
                            //generate all possible neighbours for the object
                            sequence {
                                yieldObjectNeighbors(
                                    pageWidth,
                                    pageHeight,
                                    objectsRTree,
                                    parentPanel,
                                    o,
                                    g,
                                )
                            }
                        }
                        .filterNot { consumedObjectIds.contains(it.id) }
                        .forEach { neighborObj ->
                            // this sequence will emit at least single object if there is no neighbours for it
                            consumedObjectIds += neighborObj.id

                            neighbourObjs += neighborObj
                            neighbourMBR.union(neighborObj.bbox)
                        }

                    neighbourObjs.sortWith(GroupObjectComparator(pageWidth, pageHeight, direction))

                    //Group of neighbours in the panel
                    group = PanelGroup(neighbourObjs, neighbourMBR)

                    //Create fake panel for this objects group if needed
                    panel = parentPanel ?: ComicPageObject(
                        Long.MIN_VALUE,
                        ObjectClass.PANEL,
                        neighbourMBR
                    )
                }

            objectsByPanel.getOrPut(panel) { arrayListOf() } += group
        }

    return objectsByPanel.values
        .asSequence()
        .onEach { l ->
            //sort group of objects at the panel
            l.sortWith { o1, o2 ->
                defaultComparator(
                    direction,
                    diffProportionally(
                        pageWidth,
                        pageHeight,
                        GROUP_MIN_DIFF
                    )
                ).compare(o1.mbr, o2.mbr)
            }
        }
        .flatten()
        .flatten()
        .toList()
}

/**
 * Yields all neighbour objects for provided [obj] using recursive method
 * @param objects all objects R-Tree
 * @param parentPanel [obj] parent panel
 * @param obj which neighbour objects should be found
 * @param rectangle [obj] geometry
 * @param filterObjIds object ids which should be excluded from yielding
 */
private suspend fun SequenceScope<ComicPageObject>.yieldObjectNeighbors(
    pageW: Int,
    pageH: Int,
    objects: RTree<ComicPageObject, Rectangle>,
    parentPanel: ComicPageObject?,
    obj: ComicPageObject,
    rectangle: Rectangle = obj.bbox.asRTreeGeometry(),
    filterObjIds: MutableSet<Long> = hashSetOf(),
) {
    val minDiff = diffProportionally(pageW, pageH, OBJECT_NEIGHBOUR_MIN_DIFF)

    yield(obj)

    filterObjIds += obj.id

    //search [obj] neighbours by min distance
    for ((o, g) in objects.search(rectangle, minDiff.toDouble())) {
        if (o.classId == ObjectClass.PANEL || filterObjIds.contains(o.id)) {
            continue
        }

        val sameParent = objects.search(g).let { intersections ->
            //check if intersected object has same parent panel as provided [obj]
            if (parentPanel != null) {
                intersections.firstOrNull { it.value().id == parentPanel.id } != null
            } else {
                //it shouldn't has any intersected panels
                intersections.firstOrNull { it.value().classId == ObjectClass.PANEL } == null
            }
        }

        //object's has different parent panels
        //I will assume that they cannot be grouped together
        if (!sameParent) {
            continue
        }

        //recursively emit this object neighbors
        yieldObjectNeighbors(pageW, pageH, objects, parentPanel, o, g, filterObjIds)
    }
}

private fun defaultComparator(direction: Direction, minDifference: Float = .0f) =
    simpleComparator(minDifference) { it.top }.run {
        when (direction) {
            Direction.LTR -> then(simpleComparator(minDifference) { it.left })
            Direction.RTL -> thenDescending(simpleComparator(minDifference) { it.right })
        }
    }

/**
 * Build comic book page ML object box comparator
 * @param minDifference minimum distance between two box edges that should be enough to consider them different
 * @param boxEdgeValue object box edge value (left, top, right or bottom)
 * @return object box comparator based on inputs
 */
private inline fun simpleComparator(
    minDifference: Float = .0f,
    crossinline boxEdgeValue: (RectF) -> Float
) = Comparator<RectF> { o1, o2 ->
    val v1 = boxEdgeValue(o1)
    val v2 = boxEdgeValue(o2)

    if (v1 == v2) {
        0
    } else {
        val c = v1 - v2

        // ML boxes isn't accurately. There are always some inaccuracy.
        // Firstly I compare box edge difference with required minimum
        // * Consider them equal if there is not enough difference
        // * Simple compare edge values otherwise
        if (c.absoluteValue < minDifference) {
            0
        } else {
            v1.compareTo(v2)
        }
    }
}

private fun diffProportionally(pageW: Int, pageH: Int, diff: Float): Float =
    (pageW * pageH).toFloat() * diff / (ComicHelper.PAGE_HEIGHT * ComicHelper.PAGE_WIDTH).toFloat()

/**
 * Single panel group of objects
 * @param objects objects in the group
 * @param mbr area of the group relative to page
 */
private data class PanelGroup(
    val objects: List<ComicPageObject>,
    val mbr: RectF
) : Iterable<ComicPageObject> by objects

private class GroupObjectComparator(
    pageW: Int,
    pageH: Int,
    private val direction: Direction
) : Comparator<ComicPageObject> {
    //Calculate required min difference between object edges
    //this should help to reduce ML error...I hope so
    private val minDiff = diffProportionally(pageW, pageH, OBJECT_MIN_DIFF)

    private val bbox1 = RectF()
    private val bbox2 = RectF()

    override fun compare(o1: ComicPageObject, o2: ComicPageObject) =
        if (o1.bbox == o2.bbox) {
            0
        } else {
            calculateEdgesFrom(o1.bbox, o2.bbox)

            compareBbox(bbox1, bbox2)
        }

    private fun compareBbox(b1: RectF, b2: RectF): Int {
        return when {
            b1 == b2 -> {
                0
            }
            b1.top == b2.top -> {
                /*
                    o1, o2: ▣▣
                */
                compareX(b1, b2)
            }
            else -> {
                val bTop: RectF
                val bLow: RectF
                val c: Int //comparator value

                //Calculate which object is the top most
                if (b1.top < b2.top) {
                    bTop = b1
                    bLow = b2
                    c = -1
                } else {
                    bTop = b2
                    bLow = b1
                    c = 1
                }

                when {
                    compareX(bTop, bLow) <= 0 -> {
                        // we has top left object

                        /*
                            bTop: ▣   | ▣
                            bLow:  ▣  | ▣
                        */
                        c
                    }
                    bLow.endX - bTop.startX >= bLow.width() * OBJECT_BENEATH -> {
                        // low object is also beneath the top

                        /*
                            bTop:    ▣▣▣▣
                            bLow:  ▣▣▣▣
                        */
                        c
                    }
                    else -> {
                        c * -1
                    }
                }
            }
        }
    }

    private fun calculateEdgesFrom(b1: RectF, b2: RectF) {
        val calculate = { p1: Float, p2: Float ->
            if ((p1 - p2).absoluteValue < minDiff) {
                val pMin = min(p1, p2)

                pMin to pMin
            } else {
                p1 to p2
            }
        }

        calculate(b1.left, b2.left).also { (p1, p2) ->
            bbox1.left = p1
            bbox2.left = p2
        }

        calculate(b1.top, b2.top).also { (p1, p2) ->
            bbox1.top = p1
            bbox2.top = p2
        }

        calculate(b1.right, b2.right).also { (p1, p2) ->
            bbox1.right = p1
            bbox2.right = p2
        }

        calculate(b1.bottom, b2.bottom).also { (p1, p2) ->
            bbox1.bottom = p1
            bbox2.bottom = p2
        }
    }

    /**
     * Compare provided bounding boxes by its X value
     * @param b1 first bbox
     * @param b2 second bbox
     */
    private fun compareX(b1: RectF, b2: RectF): Int =
        when (direction) {
            Direction.LTR -> compareValuesBy(b1, b2) { it.left }
            Direction.RTL -> compareValuesBy(b2, b1) { it.right }
        }

    /**
     * Start X value depends on [direction]
     */
    private val RectF.startX
        get() = when (direction) {
            Direction.LTR -> left
            Direction.RTL -> right
        }

    /**
     * End X value depends on [direction]
     */
    private val RectF.endX
        get() = when (direction) {
            Direction.LTR -> right
            Direction.RTL -> left
        }
}