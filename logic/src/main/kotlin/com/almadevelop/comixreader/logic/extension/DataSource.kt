package com.almadevelop.comixreader.logic.extension

import androidx.paging.DataSource
import androidx.paging.PagedList
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlin.coroutines.coroutineContext
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