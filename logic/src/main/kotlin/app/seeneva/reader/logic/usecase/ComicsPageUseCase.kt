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
import app.seeneva.reader.data.source.local.db.query.QueryParams as DataQueryParams

internal interface ComicsPageUseCase {
    /**
     * Get count of comic books to which [params] are applicable
     * @return comic books count
     */
    suspend fun count(params: QueryParams): Long

    /**
     * Get single comic books page data
     * @param start start index
     * @param count requested size of the page
     * @param params request params
     */
    suspend fun getPage(start: Int, count: Int, params: QueryParams): List<ComicListItem>
}

internal class ComicsPageUseCaseImpl(
    private val comicBookSource: ComicBookSource,
    private val comicTagSource: ComicTagSource,
    private val queryParamsResolver: QueryParamsResolver,
    private val localTransactionRunner: LocalTransactionRunner,
    private val mapper: ComicMetadataIntoComicListItem,
    override val dispatchers: Dispatchers,
) : ComicsPageUseCase, Dispatched {
    override suspend fun count(params: QueryParams) =
        comicBookSource.count(
            queryParamsResolver.resolveCount(
                params,
                QueryParamsResolver.FiltersEditor::addDefaultFilters
            )
        )

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

    private suspend fun QueryParams.resolve(start: Int, count: Int): DataQueryParams =
        queryParamsResolver.resolve(
            this,
            start,
            count,
            edit = QueryParamsResolver.FiltersEditor::addDefaultFilters
        )
}