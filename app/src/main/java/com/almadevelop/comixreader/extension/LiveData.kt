package com.almadevelop.comixreader.extension

import androidx.lifecycle.LiveData
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Convert [Flow] into [LiveData]
 * @param dispatchers app coroutines dispatchers
 * @return LiveData wrapper
 */
fun <T> Flow<T>.asLiveData(dispatchers: Dispatchers): LiveData<T> =
    FlowLiveData(dispatchers, this)

/**
 * Create [LiveData] using provided [action]
 * @param dispatchers
 * @param action used to construct inner flow
 */
fun <T> liveData(
    dispatchers: Dispatchers,
    action: suspend FlowCollector<T>.() -> Unit
): LiveData<T> =
    FlowLiveData(dispatchers, flow(action))

/**
 * Create [LiveData] using provided [values]
 * @param dispatchers
 * @param values used to construct inner flow
 */
fun <T> liveDataOf(dispatchers: Dispatchers, vararg values: T): LiveData<T> =
    FlowLiveData(dispatchers, flowOf(*values))

/**
 * [LiveData] wrapper around coroutine's [Flow]
 */
private class FlowLiveData<T>(
    dispatchers: Dispatchers,
    private val flow: Flow<T>
) : LiveData<T>(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext = job + dispatchers.main

    override fun onActive() {
        super.onActive()

        launch {
            flow.collect {
                value = it
            }
        }
    }

    override fun onInactive() {
        super.onInactive()
        job.cancelChildren()
    }
}