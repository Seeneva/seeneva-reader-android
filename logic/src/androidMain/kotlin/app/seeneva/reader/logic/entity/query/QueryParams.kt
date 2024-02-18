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

package app.seeneva.reader.logic.entity.query

import app.seeneva.reader.logic.entity.query.filter.Filter
import app.seeneva.reader.logic.entity.query.filter.FilterGroup

/**
 * Logic layer params for comic book titleQuery
 * @param sort sort of output titleQuery
 * @param filters filter used with the query params
 * @param titleQuery describes filter by a comic book's title
 */
class QueryParams private constructor(
    val sort: QuerySort,
    val filters: Map<FilterGroup.ID, Filter>,
    val titleQuery: String? = null
) {
    /**
     * Build new query params from the current instance
     */
    fun buildNew(f: Builder.() -> Unit): QueryParams {
        return build(this, f)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueryParams

        if (sort != other.sort) return false
        if (filters != other.filters) return false
        if (titleQuery != other.titleQuery) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sort.hashCode()
        result = 31 * result + filters.hashCode()
        result = 31 * result + (titleQuery?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "QueryParams(sort=$sort, filters=$filters, titleQuery=$titleQuery)"
    }

    interface Builder {
        var sort: QuerySort?
        var titleQuery: String?

        /**
         * Add filter into query
         * @param groupId filter group id
         * @param filter filter to add
         */
        fun addFilter(groupId: FilterGroup.ID, filter: Filter)

        /**
         * Remove query filter by filter group
         * @param groupId id of a filter's group
         */
        fun removeFilter(groupId: FilterGroup.ID)

        /**
         * Remove all filters
         */
        fun clearFilters()
    }

    private class BuilderImpl(params: QueryParams? = null) : Builder {
        override var sort: QuerySort? = params?.sort
        override var titleQuery: String? = params?.titleQuery

        //linked hash map for display in UI. Hm...
        private val filters = params?.filters?.let { LinkedHashMap(it) } ?: linkedMapOf()

        override fun addFilter(groupId: FilterGroup.ID, filter: Filter) {
            if (filter.none) {
                removeFilter(groupId)
            } else {
                filters[groupId] = filter
            }
        }

        override fun removeFilter(groupId: FilterGroup.ID) {
            filters -= groupId
        }

        override fun clearFilters() {
            filters.clear()
        }

        fun build() = QueryParams(
            sort ?: QuerySort.default(),
            filters,
            titleQuery
        )
    }

    companion object {
        fun build(params: QueryParams? = null, f: Builder.() -> Unit = {}): QueryParams {
            return BuilderImpl(params).apply(f).build()
        }
    }
}

