package com.almadevelop.comixreader.logic

import androidx.paging.DataSource
import androidx.paging.PositionalDataSource
import com.almadevelop.comixreader.common.coroutines.Dispatched
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.entity.ComicListItem
import com.almadevelop.comixreader.logic.entity.query.QueryParams
import com.almadevelop.comixreader.logic.usecase.ComicListUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlin.properties.Delegates

abstract class ComicsPagingDataSourceFactory : DataSource.Factory<Int, ComicListItem>() {
    private lateinit var currentDataSource: DataSource<Int, ComicListItem>

    var queryParams: QueryParams? by Delegates.observable(null) { _, old: QueryParams?, new: QueryParams? ->
        if (old != new) {
            if (::currentDataSource.isInitialized) {
                currentDataSource.invalidate()
            }
        }
    }

    var invalidateCallback: DataSource.InvalidatedCallback? = null

    override fun create(): DataSource<Int, ComicListItem> =
        createInner().also { currentDataSource = it }

    protected abstract fun createInner(): DataSource<Int, ComicListItem>
}

/**
 * @param parentJob used to cancel source's coroutines if parent job cancelled
 */
internal class ComicsPagingDataSource(
    override val dispatchers: Dispatchers,
    private val useCase: ComicListUseCase,
    private val queryParams: QueryParams?,
    private val additionalInvalidateCallback: InvalidatedCallback?,
    parentJob: Job?
) : PositionalDataSource<ComicListItem>(), CoroutineScope, Dispatched {
    override val coroutineContext = Job(parentJob) + dispatchers.main

    private var updatesJob: Job? = null
    private var updateFrom = 0
    private var updateTo = 0

    init {
        //cancel coroutine job if this data source marked as invalid
        addInvalidatedCallback {
            cancel()
            additionalInvalidateCallback?.onInvalidated()
        }
    }

    override fun loadInitial(
        params: LoadInitialParams,
        callback: LoadInitialCallback<ComicListItem>
    ) {
        //it seems that I can't do async work here. So I need run it on non Main Thread
        runBlocking {
            val totalCount = if (queryParams != null) {
                useCase.count(queryParams).toInt()
            } else {
                0
            }

            if (totalCount == 0) {
                callback.onResult(emptyList(), 0, 0)
            } else {
                val position = computeInitialLoadPosition(params, totalCount)
                val loadSize = computeInitialLoadSize(params, position, totalCount)

                val result = loadRangeInternal(position, loadSize)

                if (result.size == loadSize) {
                    //calculate update values for subscription
                    if (position < updateFrom) {
                        updateFrom = position
                    } else {
                        updateTo += result.size
                    }

                    callback.onResult(result, position, totalCount)
                } else {
                    invalidate()
                }
            }

            //Subscribe to data update if paging data source is still valid
            if (!isInvalid) {
                updatesJob?.cancel()

                updatesJob =
                    useCase.subscribeUpdates(updateFrom, updateTo, requireNotNull(queryParams))
                        .takeWhile { !isInvalid }
                        .take(1)
                        .onEach { invalidate() }
                        .launchIn(this@ComicsPagingDataSource)
            }
        }
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<ComicListItem>) {
        runBlocking {
            callback.onResult(loadRangeInternal(params.startPosition, params.loadSize))
        }
    }

    private suspend fun loadRangeInternal(position: Int, loadSize: Int): List<ComicListItem> {
        requireNotNull(queryParams)

        return useCase.getPage(position, loadSize, queryParams)
    }

    /**
     * Comic book pagination source factory
     *
     * @param dispatchers
     * @param useCase
     * @param parentJob optional parent job. Used to cancel inner job
     */
    class Factory(
        private val dispatchers: Dispatchers,
        private val useCase: ComicListUseCase,
        private val parentJob: Job? = null
    ) : ComicsPagingDataSourceFactory() {
        override fun createInner(): DataSource<Int, ComicListItem> =
            ComicsPagingDataSource(dispatchers, useCase, queryParams, invalidateCallback, parentJob)
    }
}