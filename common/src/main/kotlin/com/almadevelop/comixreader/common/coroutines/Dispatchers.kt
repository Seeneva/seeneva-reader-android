package com.almadevelop.comixreader.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface Dispatchers {
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
    val main: CoroutineDispatcher
    val mainImmediate: CoroutineDispatcher
}

interface Dispatched {
    val dispatchers: Dispatchers
}

suspend fun <T> Dispatched.default(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = withDispatcher(dispatchers.default, context, block)

suspend fun <T> Dispatched.io(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = withDispatcher(dispatchers.io, context, block)

suspend fun <T> Dispatched.unconfined(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = withDispatcher(dispatchers.unconfined, context, block)

suspend fun <T> Dispatched.main(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = withDispatcher(dispatchers.main, context, block)

suspend fun <T> Dispatched.immediate(
    dispatchers: Dispatchers,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = withDispatcher(dispatchers.mainImmediate, context, block)

private suspend fun <T> withDispatcher(
    dispatcher: CoroutineDispatcher,
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = withContext(dispatcher + context, block)