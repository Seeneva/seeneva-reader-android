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

import app.seeneva.reader.data.source.local.db.dao.ComicTagSource
import app.seeneva.reader.data.source.local.db.query.TagFilterType
import app.seeneva.reader.data.source.local.db.query.TagsFilters
import app.seeneva.reader.logic.entity.TagType
import app.seeneva.reader.logic.entity.query.filter.TagTypeFilter
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import app.seeneva.reader.data.source.local.db.query.CountQueryParams as DataLayerCountQueryParams
import app.seeneva.reader.data.source.local.db.query.QueryParams as DataLayerQueryParams

/**
 * Convert logic layer [QueryParams] into data layer's variant
 */
internal interface QueryParamsResolver {
    /**
     * Convert params into inner format
     * @param queryParams params to convert
     * @param start page start position
     * @param count page size
     */
    suspend fun resolve(
        queryParams: QueryParams,
        start: Int? = null,
        count: Int? = null,
        edit: suspend FiltersEditor.() -> Unit = {}
    ): DataLayerQueryParams

    /**
     * Convert params into inner 'count' format
     * @param queryParams params to convert
     */
    suspend fun resolveCount(
        queryParams: QueryParams,
        edit: suspend FiltersEditor.() -> Unit = {}
    ): DataLayerCountQueryParams

    interface FiltersEditor {
        fun addTagFilter(tagId: Long, filterType: TagFilterType)

        suspend fun addTagFilter(tagType: TagType, filterType: TagFilterType)
    }
}

/**
 * Add default tag filters
 */
internal suspend fun QueryParamsResolver.FiltersEditor.addDefaultFilters() {
    addTagFilter(TagType.TYPE_REMOVED, TagFilterType.Exclude)
}

internal class QueryParamsResolverImpl(
    private val comicTagSource: ComicTagSource
) : QueryParamsResolver {
    override suspend fun resolve(
        queryParams: QueryParams,
        start: Int?,
        count: Int?,
        edit: suspend QueryParamsResolver.FiltersEditor.() -> Unit
    ) = DataLayerQueryParams(
        count,
        start,
        queryParams.titleQuery,
        queryParams.tagsFilters(edit),
        queryParams.sort.inner
    )

    override suspend fun resolveCount(
        queryParams: QueryParams,
        edit: suspend QueryParamsResolver.FiltersEditor.() -> Unit
    ) = DataLayerCountQueryParams(queryParams.titleQuery, queryParams.tagsFilters(edit))

    /**
     * Retrieve query tags filters
     */
    private suspend fun QueryParams.tagsFilters(edit: suspend QueryParamsResolver.FiltersEditor.() -> Unit): TagsFilters {
        return FiltersEditorImpl(comicTagSource).also {
            filters.forEach { (_, filter) ->
                coroutineContext.ensureActive()

                if (filter is TagTypeFilter) {
                    it.addTagFilter(filter.tagType, filter.filterType)
                }
            }

            it.edit()
        }.filters.takeIf { it.isNotEmpty() }
    }

    private class FiltersEditorImpl(
        private val tagSource: ComicTagSource
    ) : QueryParamsResolver.FiltersEditor {
        val filters: Map<Long, TagFilterType>
            get() = filtersInner

        private val filtersInner: MutableMap<Long, TagFilterType> = hashMapOf()

        override fun addTagFilter(tagId: Long, filterType: TagFilterType) {
            filtersInner[tagId] = filterType
        }

        override suspend fun addTagFilter(tagType: TagType, filterType: TagFilterType) {
            tagSource.findByType(tagType.ordinal).let {
                if (it == null && filterType == TagFilterType.Include) {
                    //if we don't have such tag yet than ignore all comic books by adding impossible tag id
                    Long.MIN_VALUE
                } else {
                    it?.id
                }
            }?.also { tagId ->
                filtersInner[tagId] = filterType
            }
        }
    }
}