/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
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

package app.seeneva.reader.logic.storage

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.test.*

class ObjectStorageImplTest {
    @MockK(relaxUnitFun = true)
    private lateinit var storageSource: ObjectStorageImpl.Source<Long, Pair<Long, Int>>

    //It will be injected into storage object
    private val storageContext: CoroutineContext = Dispatchers.IO + Job()

    //For injection
    private val releaseDelay = 0L

    @InjectMockKs(injectImmutable = true)
    private lateinit var storage: ObjectStorageImpl<Long, Pair<Long, Int>>

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @AfterTest
    fun after() {
        storage.close()
    }

    @Test
    fun `concurrent support`() {
        defaultMock()

        // Make a lot of concurrent 'get' calls to provided
        val flow = channelFlow {
            repeat(100) {
                launch(Dispatchers.IO) {
                    delay(Random.nextLong(1000, 3000))

                    val obj = storage.borrow(Random.nextLong(50))

                    send(obj)
                }
            }
        }.buffer(100)

        runBlocking {
            // Listen to generated page positions and concurrently close each page
            launch(Dispatchers.IO) {
                flow.onEach { delay(Random.nextLong(30)) }.collect {
                    it.returnObject()
                }
            }
        }

        assertEquals(0, storage.count, "Storage should be empty now")

        coVerify(atMost = 100) {
            storageSource.new(any())
            storageSource.onReleased(any())
        }

        storage.close()

        assertTrue(storage.closed, "Storage should be closed now")
    }

    @Test
    fun borrowing() {
        defaultMock()

        runBlocking {
            storage.borrow(0)
            val borrow1 = storage.borrow(0)
            val borrow1Value = borrow1.borrowedObject()

            // Borrow and release
            val borrow2Value = storage.withBorrow(1) { it }

            coVerifyOrder {
                storageSource.new(0)
                storageSource.new(1)
                storageSource.onReleased(borrow2Value)
            }

            assertEquals(0, borrow1.borrowedObject().first)

            // Check what happen after return borrowed object
            borrow1.returnObject()

            assertFailsWith<IllegalStateException> { borrow1.borrowedObject() }

            borrow1.returnObject()

            // but object shouldn't be released 'cause we have 1 more borrow to it
            coVerify(exactly = 0) { storageSource.onReleased(borrow1Value) }

            // We have exactly one object in the storage
            assertEquals(1, storage.count)

            // Close and wait until storage job finish
            storage.close()
            storageContext.job.join()

            assertTrue(storage.closed)

            // Now our storage is empty
            assertEquals(0, storage.count)

            //And borrowed object was released
            coVerify(exactly = 1) { storageSource.onReleased(borrow1Value) }

            // We can't use this storage anymore
            assertFailsWith<IllegalStateException> { storage.borrow(1) }
        }
    }

    private fun defaultMock() {
        val keySlot = slot<Long>()
        val objSlot = slot<Pair<Long, Int>>()

        coEvery { storageSource.new(capture(keySlot)) } coAnswers { keySlot.captured to Random.nextInt() }

        every { storageSource.key(capture(objSlot)) } answers { objSlot.captured.first }
    }
}