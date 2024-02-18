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

import androidx.annotation.StringRes
import app.seeneva.reader.logic.R
import app.seeneva.reader.data.source.local.db.query.QuerySort as QuerySortInner

/**
 * Represent comic books sort type
 * @param inner inner sort type to wrap
 * @param titleResId title string resource id
 */
class QuerySort private constructor(
    internal val inner: QuerySortInner,
    @StringRes val titleResId: Int
) {
    /**
     * key of the sort type
     */
    val key: String
        get() = inner.name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QuerySort

        if (inner != other.inner) return false
        if (titleResId != other.titleResId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = inner.hashCode()
        result = 31 * result + titleResId
        return result
    }

    override fun toString(): String {
        return "QuerySort(inner=$inner, titleResId=$titleResId)"
    }

    companion object {
        /**
         * Return all available sort types
         */
        fun all(): Array<QuerySort> = QuerySortInner.values().let { array ->
            Array(array.size) { array[it].intoQuerySort() }
        }

        /**
         * Build and return default solrt type
         */
        fun default(): QuerySort = QuerySortInner.OpenTimeDesc.intoQuerySort()

        /**
         * Return sort type bt it key or null
         * @return sort type or null in case of any error
         */
        internal fun fromKey(key: String): QuerySort? =
            runCatching { QuerySortInner.valueOf(key) }.map { it.intoQuerySort() }.getOrNull()

        /**
         * Map inner sort type into logic sort type
         */
        private fun QuerySortInner.intoQuerySort() =
            when (this) {
                QuerySortInner.OpenTimeDesc -> QuerySort(
                    this,
                    R.string.sort_comic_list_time_desc
                )
                QuerySortInner.OpenTimeAsc -> QuerySort(
                    this,
                    R.string.sort_comic_list_time_asc
                )
                QuerySortInner.NameAsc -> QuerySort(
                    this,
                    R.string.sort_comic_list_name_asc
                )
                QuerySortInner.NameDesc -> QuerySort(
                    this,
                    R.string.sort_comic_list_name_desc
                )
            }
    }
}