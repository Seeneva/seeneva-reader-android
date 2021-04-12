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

package app.seeneva.reader

import app.seeneva.reader.viewmodel.EventSender
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