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

package app.seeneva.reader.logic.entity.query.filter

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import app.seeneva.reader.data.source.local.db.query.TagFilterType
import app.seeneva.reader.logic.R
import app.seeneva.reader.logic.entity.TagType

interface FilterProvider {
    /**
     * @return all available filter groups
     */
    fun groups(): List<FilterGroup>
}

internal class FilterProviderImpl(context: Context) : FilterProvider {
    private val context = context.applicationContext

    override fun groups(): List<FilterGroup> =
        FilterGroupBuilderImpl(context.resources).apply {
            group(FilterGroup.ID.COMPLETION_STATUS, R.string.filter_comic_completed_title) {
                CompletionFilterType.values()
                    .forEach {
                        when (it) {
                            CompletionFilterType.NONE ->
                                dummyFilter(it.name, R.string.filter_comic_completed_any)
                            CompletionFilterType.NOT_COMPLETED ->
                                excludeTypeFilter(
                                    it.name,
                                    R.string.filter_comic_completed_not,
                                    TagType.TYPE_COMPLETED
                                )
                            CompletionFilterType.ONLY_COMPLETED ->
                                includeTypeFilter(
                                    it.name,
                                    R.string.filter_comic_completed_only,
                                    TagType.TYPE_COMPLETED
                                )
                        }
                    }
            }

            group(FilterGroup.ID.FILE_STATUS, R.string.filter_comic_file_status_title) {
                FileStatusFilterType.values()
                    .forEach {
                        when (it) {
                            FileStatusFilterType.NONE ->
                                dummyFilter(
                                    it.name,
                                    R.string.filter_comic_file_status_any
                                )
                            FileStatusFilterType.ONLY_CORRUPTED ->
                                includeTypeFilter(
                                    it.name,
                                    R.string.filter_comic_file_status_corrupted,
                                    TagType.TYPE_CORRUPTED
                                )
                        }
                    }
            }
        }.build()

    private interface FilterGroupBuilder {
        /**
         * Add a new filter group
         * @param id filter group id
         * @param titleResId  filter group title
         * @param filterBuilder
         */
        fun group(
            id: FilterGroup.ID,
            @StringRes titleResId: Int,
            filterBuilder: FilterBuilder.() -> Unit
        ): FilterGroupBuilder
    }

    private class FilterGroupBuilderImpl(private val res: Resources) : FilterGroupBuilder {
        private val filterGroups = arrayListOf<FilterGroup>()

        /**
         * Add a new filter group
         * @param id filter group id
         * @param titleResId  filter group title
         * @param filterBuilder
         */
        override fun group(
            id: FilterGroup.ID,
            @StringRes titleResId: Int,
            filterBuilder: FilterBuilder.() -> Unit
        ): FilterGroupBuilder {
            filterGroups += FilterGroup(
                id,
                res.getString(titleResId),
                FilterBuilderImpl(res)
                    .apply(filterBuilder)
                    .build()
            )

            return this
        }

        fun build(): List<FilterGroup> {
            require(filterGroups.isNotEmpty()) { "Filter groups should have at least one group" }

            return filterGroups
        }
    }

    private interface FilterBuilder {
        fun includeTypeFilter(
            id: String,
            @StringRes titleResId: Int,
            tagType: TagType
        ): FilterBuilder

        fun excludeTypeFilter(
            id: String,
            @StringRes titleResId: Int,
            tagType: TagType
        ): FilterBuilder

        fun dummyFilter(
            id: String,
            @StringRes titleResId: Int
        ): FilterBuilder

        fun filter(filter: Filter): FilterBuilder
    }

    private class FilterBuilderImpl(private val res: Resources) : FilterBuilder {
        private val filters = arrayListOf<Filter>()

        override fun includeTypeFilter(
            id: String,
            @StringRes titleResId: Int,
            tagType: TagType
        ) = typeFilterInner(id, titleResId, tagType, TagFilterType.Include)

        override fun excludeTypeFilter(
            id: String,
            @StringRes titleResId: Int,
            tagType: TagType
        ) = typeFilterInner(id, titleResId, tagType, TagFilterType.Exclude)

        override fun dummyFilter(id: String, @StringRes titleResId: Int) =
            filter(DummyFilter(id, res.getString(titleResId)))

        override fun filter(filter: Filter): FilterBuilder {
            filters += filter

            return this
        }

        fun build(): List<Filter> {
            require(filters.isNotEmpty()) { "Filer group should have at least one filter" }

            return filters
        }

        private fun typeFilterInner(
            id: String,
            @StringRes title: Int,
            tagType: TagType,
            filterType: TagFilterType
        ): FilterBuilder {
            return filter(TagTypeFilter(id, res.getString(title), tagType, filterType))
        }
    }

    private enum class CompletionFilterType { NONE, ONLY_COMPLETED, NOT_COMPLETED }

    private enum class FileStatusFilterType { NONE, ONLY_CORRUPTED }
}