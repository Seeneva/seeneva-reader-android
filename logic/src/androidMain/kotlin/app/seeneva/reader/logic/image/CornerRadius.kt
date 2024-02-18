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

package app.seeneva.reader.logic.image

/**
 * Describes an image corner radius
 * @param topLeft
 * @param topRight
 * @param bottomRight
 * @param bottomLeft
 */
data class CornerRadius(
    val topLeft: Float = .0f,
    val topRight: Float = .0f,
    val bottomRight: Float = .0f,
    val bottomLeft: Float = .0f
) {
    init {
        require(topLeft >= .0f)
        require(topRight >= .0f)
        require(bottomRight >= .0f)
        require(bottomLeft >= .0f)
    }

    /**
     * Are all corners have non zero values
     */
    val hasRoundCorners: Boolean
        get() = topLeft > .0f || topRight > .0f || bottomLeft > .0f || bottomRight > .0f

    /**
     * Are all corners have same values
     */
    val equalCorners: Boolean
        get() = topLeft == topRight && topLeft == bottomLeft && topLeft == bottomRight
}