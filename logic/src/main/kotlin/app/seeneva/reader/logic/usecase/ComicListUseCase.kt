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

package app.seeneva.reader.logic.usecase

import app.seeneva.reader.common.coroutines.Dispatched
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.common.coroutines.io
import app.seeneva.reader.data.source.local.db.LocalTransactionRunner
import app.seeneva.reader.data.source.local.db.dao.ComicBookSource
import app.seeneva.reader.data.source.local.db.dao.ComicTagSource
import app.seeneva.reader.data.source.local.db.entity.SimpleComicBookWithTags
import app.seeneva.reader.logic.entity.ComicListItem
import app.seeneva.reader.logic.entity.TagType
import app.seeneva.reader.logic.entity.query.QueryParams
import app.seeneva.reader.logic.entity.query.QueryParamsResolver
import app.seeneva.reader.logic.entity.query.addDefaultFilters
import app.seeneva.reader.logic.extension.getHardcodedTagId
import app.seeneva.reader.logic.extension.hasTag
import app.seeneva.reader.logic.mapper.ComicMetadataIntoComicListItem
import kotlinx.coroutines.flow.*
import app.seeneva.reader.data.source.local.db.query.QueryParams as DataLayerQueryParams

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
                .map { }
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