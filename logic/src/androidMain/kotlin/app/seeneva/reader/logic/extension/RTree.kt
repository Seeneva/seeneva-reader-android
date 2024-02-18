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

package app.seeneva.reader.logic.extension

import android.graphics.RectF
import app.seeneva.reader.logic.entity.ComicPageObject
import com.github.davidmoten.rtree2.Entries
import com.github.davidmoten.rtree2.Entry
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Geometry
import com.github.davidmoten.rtree2.geometry.Rectangle
import java.util.Optional

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