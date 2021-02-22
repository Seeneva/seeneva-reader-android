package com.almadevelop.comixreader.extension

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.whenStateAtLeast
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Start [Flow] values collector. Values will be send to [observer] only when
 * current [Lifecycle.State] is at least [minState]
 * @param lifecycleOwner used to start coroutine and check current [Lifecycle.State]
 * @param minState state when [observer] starts to receive [Flow] values
 * @param observer function which will receive [Flow] values on [lifecycleOwner] dispatcher.
 * @return started [Job] which can be cancelled
 */
inline fun <T> Flow<T>.observe(
    lifecycleOwner: LifecycleOwner,
    minState: Lifecycle.State = Lifecycle.State.STARTED,
    crossinline observer: suspend (T) -> Unit
): Job = observe(lifecycleOwner.lifecycle, minState, observer)

inline fun <T> Flow<T>.observe(
    lifecycle: Lifecycle,
    minState: Lifecycle.State = Lifecycle.State.STARTED,
    crossinline observer: suspend (T) -> Unit
): Job = lifecycle.coroutineScope
    .launch {
        collectLatest {
            lifecycle.whenStateAtLeast(minState) {
                observer(it)
            }
        }
    }