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

import app.seeneva.reader.data.entity.ComicTag

internal enum class TagType {
    /**
     * Tags created by user
     */
    TYPE_USER,
    /**
     * Comic books marked as completed
     */
    TYPE_COMPLETED,
    /**
     * Comic books marked as removed
     */
    TYPE_REMOVED,
    /**
     * Comic books marked as broken (without read access)
     */
    TYPE_CORRUPTED;

    val hardcoded: Boolean
        get() = this != TYPE_USER

    /**
     * Build and return [ComicTag]
     * @return result comic book tag
     */
    fun newHardcodedTag(): ComicTag =
        ComicTag(0, name = name, type = ordinal)
}