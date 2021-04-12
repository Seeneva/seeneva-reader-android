/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
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

package app.seeneva.reader.data.source.local.db.query

private typealias TagId = Long

typealias TagsFilters = Map<TagId, TagFilterType>?

interface PagedQueryParams {
    /**
     * limit how many items
     */
    val limit: Int?
    /**
     * offset from the start
     */
    val offset: Int?
}

interface FilterQueryParams {
    val title: String?
    val tagsFilters: TagsFilters
}

/**
 * @param limit how many items
 * @param offset offset from the start
 * @param title comic book title should contain it
 * @param sort how to sort result list
 */
data class QueryParams(
    override val limit: Int? = null,
    override val offset: Int? = null,
    override val title: String? = null,
    override val tagsFilters: TagsFilters = null,
    val sort: QuerySort? = null
) : PagedQueryParams,
    FilterQueryParams {
    companion object {
        val EMPTY =
            QueryParams()
    }
}

data class CountQueryParams(
    override val title: String? = null,
    override val tagsFilters: TagsFilters = null
) : FilterQueryParams {
    companion object {
        val EMPTY =
            CountQueryParams()
    }
}

enum class QuerySort {
    OpenTimeDesc,
    OpenTimeAsc,
    NameAsc,
    NameDesc;
}

enum class TagFilterType { Include, Exclude }