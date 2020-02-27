package com.almadevelop.comixreader

import com.almadevelop.comixreader.common.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher

object AppDispatchers : Dispatchers {
    override val default: CoroutineDispatcher
        get() = kotlinx.coroutines.Dispatchers.Default
    override val io: CoroutineDispatcher
        get() = kotlinx.coroutines.Dispatchers.IO
    override val unconfined: CoroutineDispatcher
        get() = kotlinx.coroutines.Dispatchers.Unconfined
    override val main: CoroutineDispatcher
        get() = kotlinx.coroutines.Dispatchers.Main
    override val mainImmediate: CoroutineDispatcher
        get() = kotlinx.coroutines.Dispatchers.Main.immediate
}