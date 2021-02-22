package com.almadevelop.comixreader

import com.almadevelop.comixreader.viewmodel.EventSender
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.amshove.kluent.coInvoking
import org.amshove.kluent.shouldContainAll
import org.amshove.kluent.shouldNotThrow
import org.amshove.kluent.withMessage
import org.junit.Test
import java.util.concurrent.Executors

class EventSenderTest {
    @Test
    fun `sender emits all events`() {
        val sender = EventSender<Int>()

        runBlocking {
            launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
                coInvoking {
                    val events = withTimeout(500) { sender.eventState.take(2).toList() }

                    events shouldContainAll arrayOf(1, 2)
                } shouldNotThrow TimeoutCancellationException::class withMessage "EventSender doesn't send events properly"
            }

            launch(Dispatchers.IO) {
                delay(200)
                sender.send(2)
            }

            delay(100)

            sender.send(1)
        }
    }
}