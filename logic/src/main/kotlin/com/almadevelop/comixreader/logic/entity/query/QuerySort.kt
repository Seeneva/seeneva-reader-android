package com.almadevelop.comixreader.logic.entity.query

import androidx.annotation.StringRes
import com.almadevelop.comixreader.data.source.local.db.query.QuerySort as QuerySortInner
import com.almadevelop.comixreader.logic.R

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