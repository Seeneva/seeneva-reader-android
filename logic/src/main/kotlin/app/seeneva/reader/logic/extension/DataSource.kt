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

package app.seeneva.reader.logic.extension

import androidx.paging.DataSource
import androidx.paging.PagedList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.resume

/**
 * Create [Flow] which emit new [DataSource] every time it invalidated
 */
fun <K, V> DataSource.Factory<K, V>.asFlow(): Flow<DataSource<K, V>> =
    flow {
        while (currentCoroutineContext().isActive) {
            val dataSource = create()

            try {
                emit(dataSource)

                dataSource.waitInvalidate()
            } finally {
                dataSource.invalidate()
            }
        }
    }

/**
 * Create [PagedList] [Flow] from a [DataSource]
 * @param config config of the output [PagedList]
 * @param initialKey key for the first [PagedList] loading
 * @param boundaryCallback boundary callback for the output [PagedList]
 * @param notifyDispatcher will be used as the output [PagedList] notifyExecutor
 * @param fetchDispatcher
 */
fun <K, V> Flow<DataSource<K, V>>.toPagedList(
    config: PagedList.Config,
    initialKey: K? = null,
    boundaryCallback: PagedList.BoundaryCallback<V>? = null,
    notifyDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    fetchDispatcher: CoroutineDispatcher = Dispatchers.IO
): Flow<PagedList<V>> =
    transformLatest {
        val notifyExecutor = notifyDispatcher.asExecutor()
        val fetchExecutor = fetchDispatcher.asExecutor()

        var prevPagedList: PagedList<V>? = null

        collect { dataSource ->
            val pagedList = PagedList(
                dataSource,
                config,
                notifyExecutor,
                fetchExecutor,
                boundaryCallback,
                prevPagedList?.lastKey as? K ?: initialKey
            )

            emit(pagedList)

            prevPagedList = pagedList
        }
    }.filterNot { it.isDetached }
        .flowOn(fetchDispatcher)

/**
 * Suspend till [DataSource] invalidate
 */
suspend fun DataSource<*, *>.waitInvalidate() {
    suspendCancellableCoroutine<Unit> { cont ->
        if (isInvalid) {
            cont.resume(Unit)
        } else {
            val invalidateCallback = DataSource.InvalidatedCallback {
                cont.resume(Unit)
            }

            addInvalidatedCallback(invalidateCallback)

            cont.invokeOnCancellation { removeInvalidatedCallback(invalidateCallback) }
        }
    }
}