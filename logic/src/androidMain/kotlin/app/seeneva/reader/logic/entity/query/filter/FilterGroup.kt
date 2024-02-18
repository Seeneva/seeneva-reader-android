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

package app.seeneva.reader.logic.entity.query.filter

/**
 * Represents available filter group
 *
 * @param id filter group id
 * @param title filter group title
 * @param filters child filters
 */
data class FilterGroup(
    val id: ID,
    val title: String,
    val filters: List<Filter>
) : Iterable<Filter> {
    operator fun get(index: Int) = filters[index]

    override fun iterator(): Iterator<Filter> {
        return filters.listIterator()
    }

    enum class ID { COMPLETION_STATUS, FILE_STATUS }
}