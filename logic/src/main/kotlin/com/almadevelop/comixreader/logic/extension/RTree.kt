package com.almadevelop.comixreader.logic.extension

import android.graphics.RectF
import com.almadevelop.comixreader.logic.entity.ComicPageObject
import com.github.davidmoten.rtree2.Entries
import com.github.davidmoten.rtree2.Entry
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Geometry
import com.github.davidmoten.rtree2.geometry.Rectangle
import java.util.*

/**
 * Represent provided [RectF] as RTree [com.github.davidmoten.rtree2.geometry.Rectangle]
 * @return RTree rectangle
 */
internal fun RectF.asRTreeGeometry() = Geometries.rectangle(left, top, right, bottom)

/**
 * Represent RTree Rectangle as Android RectF
 * @return Android RectF
 */
internal fun Rectangle.asRectF(target: RectF = RectF()) =
    target.also { it.set(x1().toFloat(), y1().toFloat(), x2().toFloat(), y2().toFloat()) }

/**
 * Return empty [RectF] in case of empty [Optional]
 */
internal fun Optional<Rectangle>.asRectF(target: RectF = RectF()) =
    map { it.asRectF(target) }.orElseGet { target.also { it.setEmpty() } }

/**
 * Represent object as R-Tree entry
 */
internal fun ComicPageObject.asRTreeEntry() = Entries.entry(this, bbox.asRTreeGeometry())

internal operator fun <T, S : Geometry> Entry<T, S>.component1(): T = value()
internal operator fun <T, S : Geometry> Entry<T, S>.component2(): S = geometry()