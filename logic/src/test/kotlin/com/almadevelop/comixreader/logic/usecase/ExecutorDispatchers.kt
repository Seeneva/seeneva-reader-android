import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.withContext
import org.junit.Test

//package com.almadevelop.comixreader.logic.usecase
//
//import com.almadevelop.comixreader.logic.Dispatchers
//import kotlinx.coroutines.CoroutineDispatcher
//import kotlinx.coroutines.asCoroutineDispatcher
//import kotlinx.coroutines.internal.isMissing
//import kotlinx.coroutines.test.setMain
//import java.io.Closeable
//import java.util.concurrent.ExecutorService
//import java.util.concurrent.Executors
//
//class ExecutorDispatchers(executor: ExecutorService = Executors.newSingleThreadExecutor()) : Dispatchers,
//    Closeable {
//    private val dispatcher = executor.asCoroutineDispatcher()
//
//    override val io: CoroutineDispatcher
//        get() = dispatcher
//    override val main: CoroutineDispatcher
//        get() = dispatcher
//    override val mainImmediate: CoroutineDispatcher
//        get() = dispatcher
//
//    override fun close() {
//        dispatcher.close()
//    }
//}