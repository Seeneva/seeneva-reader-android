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

package app.seeneva.reader.logic

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingState
import app.seeneva.reader.data.source.local.db.LocalTransactionRunner
import app.seeneva.reader.logic.entity.ComicListItem
import app.seeneva.reader.logic.entity.query.QueryParams
import app.seeneva.reader.logic.usecase.ComicsPageUseCase
import org.tinylog.kotlin.Logger

/**
 * Comics paging source.
 *
 * Key here is the page start index (zero based) (e.g. '0, 15, 30' if loadSize == 15)
 *
 * @param useCase list use case to use
 * @param transaction local transaction runner
 * @param queryParams requested comics query parameters
 * @param defaultLoadSize default load size. It can be different from [LoadParams.loadSize]
 */
internal class ComicsPagingDataSource(
    private val useCase: ComicsPageUseCase,
    private val transaction: LocalTransactionRunner,
    private val queryParams: QueryParams,
    private val defaultLoadSize: Int,
) : PagingSource<Int, ComicListItem>() {
    private var totalCount = COUNT_UNDEFINED

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ComicListItem> {
        // Page's first item index relatively to full items list in the database
        val pageStartIndex = params.key?.coerceAtLeast(0) ?: 0

        Logger.debug { "Start loading comics page starting from $pageStartIndex. Requested size: ${params.loadSize}" }

        val pageData = transaction.run {
            if (totalCount == COUNT_UNDEFINED) {
                totalCount = useCase.count(queryParams)
            }

            if (totalCount == 0L) {
                emptyList()
            } else {
                useCase.getPage(
                    pageStartIndex,
                    when (params) {
                        is LoadParams.Refresh -> params.loadSize
                        else -> defaultLoadSize
                    },
                    queryParams
                )
            }
        }

        if (pageData.isEmpty()) {
            return LoadResult.Page(pageData, null, null, 0, 0)
        }

        val page = LoadResult.Page(
            data = pageData,
            prevKey = if (pageStartIndex == 0) {
                null
            } else {
                // it is possible to have negative value so force it to zero
                (pageStartIndex - defaultLoadSize).coerceAtLeast(0)
            },
            nextKey = (pageStartIndex + pageData.size).takeIf { it < totalCount },
            itemsBefore = if (pageStartIndex == 0) 0 else pageStartIndex - 1,
            itemsAfter = (totalCount - (pageStartIndex + pageData.size)).toInt()
        )

        Logger.debug { "Comics page loaded. Start index: $pageStartIndex. Page: $page" }

        return page
    }

    override fun getRefreshKey(state: PagingState<Int, ComicListItem>) =
        state.anchorPosition?.let {
            if (it < state.config.initialLoadSize) {
                // if anchor position is less than initial loading count then download from the beginning
                0
            } else {
                // otherwise load a page around anchorPosition using initialLoadSize
                (it - state.config.initialLoadSize / 2).coerceAtLeast(0)
            }
        }

    companion object {
        private const val COUNT_UNDEFINED = Long.MIN_VALUE
    }
}