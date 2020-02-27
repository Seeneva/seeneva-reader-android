package com.almadevelop.comixreader.logic.entity.query

import com.almadevelop.comixreader.data.source.local.db.query.TagFilterType
import com.almadevelop.comixreader.data.source.local.db.query.TagsFilters
import com.almadevelop.comixreader.data.source.local.db.dao.ComicTagSource
import com.almadevelop.comixreader.logic.entity.TagType
import com.almadevelop.comixreader.logic.entity.query.filter.TagTypeFilter
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import com.almadevelop.comixreader.data.source.local.db.query.CountQueryParams as DatyaLayerCountQueryParams
import com.almadevelop.comixreader.data.source.local.db.query.QueryParams as DataLayerQueryParams

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
    ): DatyaLayerCountQueryParams

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
    ) = DatyaLayerCountQueryParams(queryParams.titleQuery, queryParams.tagsFilters(edit))

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
        }.apply { edit() }
            .filters
            .takeIf { it.isNotEmpty() }
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
                    //if we don't have such tag yet than ignore all comic books
                    0L
                } else {
                    it?.id
                }
            }?.also { tagId ->
                filtersInner[tagId] = filterType
            }
        }
    }
}