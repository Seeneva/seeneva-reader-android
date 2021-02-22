package com.almadevelop.comixreader.logic.usecase

import com.almadevelop.comixreader.common.coroutines.Dispatched
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.common.coroutines.io
import com.almadevelop.comixreader.data.source.local.db.LocalTransactionRunner
import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookSource
import com.almadevelop.comixreader.data.source.local.db.dao.ComicTagSource
import com.almadevelop.comixreader.data.source.local.db.entity.SimpleComicBookWithTags
import com.almadevelop.comixreader.logic.entity.ComicListItem
import com.almadevelop.comixreader.logic.entity.TagType
import com.almadevelop.comixreader.logic.entity.query.QueryParams
import com.almadevelop.comixreader.logic.entity.query.QueryParamsResolver
import com.almadevelop.comixreader.logic.entity.query.addDefaultFilters
import com.almadevelop.comixreader.logic.extension.getHardcodedTagId
import com.almadevelop.comixreader.logic.extension.hasTag
import com.almadevelop.comixreader.logic.mapper.ComicMetadataIntoComicListItem
import kotlinx.coroutines.flow.*
import com.almadevelop.comixreader.data.source.local.db.query.QueryParams as DataLayerQueryParams

interface ComicListUseCase {
    /**
     * Get count of comic books to which [params] are applicable
     * @return comic books count
     */
    suspend fun count(params: QueryParams): Long

    /**
     * Get total count of all not removed comic books
     * @return comic books count
     */
    suspend fun totalCount(params: QueryParams): Long

    suspend fun getPage(start: Int, count: Int, params: QueryParams): List<ComicListItem>

    /**
     * Create [Flow] which will emit when comic books have been changed
     * @param params comic book query params
     */
    fun subscribeUpdates(params: QueryParams): Flow<Unit>
}

private suspend fun QueryParamsResolver.FiltersEditor.defaultResolverEditor() {
    addDefaultFilters()
}

internal class ComicListUseCaseImpl(
    private val comicBookSource: ComicBookSource,
    private val comicTagSource: ComicTagSource,
    private val queryParamsResolver: QueryParamsResolver,
    private val localTransactionRunner: LocalTransactionRunner,
    override val dispatchers: Dispatchers,
    private val mapper: ComicMetadataIntoComicListItem
) : ComicListUseCase, Dispatched {

    override suspend fun count(params: QueryParams) =
        comicBookSource.count(
            queryParamsResolver.resolveCount(
                params,
                QueryParamsResolver.FiltersEditor::defaultResolverEditor
            )
        )

    override suspend fun totalCount(params: QueryParams): Long {
        //remove any user added filters to get total comic book count
        //Maybe it should be changed when query by tags will be implemented?
        return comicBookSource.count(
            queryParamsResolver.resolveCount(
                params.buildNew {
                    clearFilters()
                    titleQuery = null
                }, QueryParamsResolver.FiltersEditor::defaultResolverEditor
            )
        )
    }

    override suspend fun getPage(start: Int, count: Int, params: QueryParams): List<ComicListItem> {
        fun SimpleComicBookWithTags.hasTagInner(id: Long?): Boolean {
            return id?.let { hasTag(it) } ?: false
        }

        return localTransactionRunner.run {
            val completedTagId = comicTagSource.getHardcodedTagId(TagType.TYPE_COMPLETED)
            val corruptedTagId = comicTagSource.getHardcodedTagId(TagType.TYPE_CORRUPTED)

            val page = comicBookSource.querySimpleWithTags(params.resolve(start, count))

            io {
                page.map {
                    mapper(it, it.hasTagInner(completedTagId), it.hasTagInner(corruptedTagId))
                }
            }
        }
    }

    override fun subscribeUpdates(params: QueryParams) =
        flow {
            emitAll(comicBookSource.subscribeSimpleWithTags(params.resolve(0, 0))
                .drop(1) //we do not want to receive first emit
                .map { Unit }
                .conflate()
                .flowOn(dispatchers.io))
        }

    private suspend fun QueryParams.resolve(start: Int, count: Int): DataLayerQueryParams =
        queryParamsResolver.resolve(
            this,
            start,
            count,
            edit = QueryParamsResolver.FiltersEditor::defaultResolverEditor
        )
}