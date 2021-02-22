package com.almadevelop.comixreader.viewmodel

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll

/**
 * Does not store events after send process
 */
class EventSender<E> {
    private val _eventState =
        MutableSharedFlow<E>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val eventState: Flow<E>
        get() = _eventState

    fun send(event: E) {
        //offer and send will never suspend because of conflated channel
        _eventState.tryEmit(event)
    }

    suspend fun sendAll(src: Flow<E>){
        _eventState.emitAll(src)
    }
}