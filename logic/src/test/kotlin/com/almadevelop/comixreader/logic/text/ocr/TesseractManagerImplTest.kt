package com.almadevelop.comixreader.logic.text.ocr

import com.almadevelop.comixreader.data.entity.ml.Tesseract
import com.almadevelop.comixreader.logic.usecase.ml.text.TesseractUseCase
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

class TesseractManagerImplTest {
    @MockK
    private lateinit var useCase: TesseractUseCase

    private val coroutineContext: CoroutineContext = Dispatchers.IO + Job()

    @InjectMockKs
    private lateinit var tesseractManager: TesseractManagerImpl

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @AfterTest
    fun after() {
        tesseractManager.close()
    }

    @Test
    fun `init and release`() {
        val tesseract = mockk<Tesseract>(relaxUnitFun = true)

        coEvery { useCase.init(any()) } coAnswers { tesseract }

        runBlocking {
            repeat(100) {
                launch(Dispatchers.IO) {
                    tesseractManager.init()
                }
            }

            withTimeout(300) {
                val state = tesseractManager.state.firstStateInstance<TesseractState.Initialized>()

                assertEquals(tesseract, state.tesseract)
            }

            coVerify(exactly = 1) { useCase.init(any()) }

            tesseractManager.release()

            withTimeout(300) {
                tesseractManager.state.firstStateInstance<TesseractState.Empty>()
            }

            tesseractManager.init()

            withTimeout(300) {
                val state = tesseractManager.state.firstStateInstance<TesseractState.Initialized>()

                assertEquals(tesseract, state.tesseract)
            }

            tesseractManager.close()

            withTimeout(300) {
                tesseractManager.state.firstStateInstance<TesseractState.Empty>()
            }

            verify(exactly = 2) { tesseract.close() }

            assertFailsWith<IllegalStateException> { tesseractManager.init() }

            assertFailsWith<IllegalStateException> { tesseractManager.release() }
        }
    }

    @Test
    fun `long init cancellation`() {
        coEvery { useCase.init(any()) } coAnswers {
            delay(Long.MAX_VALUE)

            mockk()
        }

        tesseractManager.init()

        runBlocking {
            delay(100)

            tesseractManager.release()

            withTimeout(300) {
                tesseractManager.state.firstStateInstance<TesseractState.Empty>()
            }
        }
    }

    private suspend inline fun <reified T : TesseractState> StateFlow<TesseractState>.firstStateInstance() =
        filterIsInstance<T>().first()
}