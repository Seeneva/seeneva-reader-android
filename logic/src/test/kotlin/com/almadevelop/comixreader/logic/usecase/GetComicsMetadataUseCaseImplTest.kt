//package com.almadevelop.comixreader.logic.usecase
//
//import android.net.Uri
//import com.almadevelop.comixreader.data.entity.ComicsMetadata
//import com.almadevelop.comixreader.data.source.jni.NativeSource
//import com.almadevelop.comixreader.data.source.local.ComicBookMetadataSource
//import io.mockk.*
//import io.mockk.impl.annotations.MockK
//import kotlinx.coroutines.runBlocking
//import org.junit.After
//import org.junit.Before
//import org.junit.Test
//
//class GetComicsMetadataUseCaseImplTest {
//    @MockK
//    lateinit var nativeSource: NativeSource
//    @MockK
//    lateinit var localSource: ComicBookMetadataSource
//
//    private lateinit var useCase: AddComicUseCaseImpl
//
//    private lateinit var dispatchers: ExecutorDispatchers
//
//    @Before
//    fun setup() {
//        MockKAnnotations.init(this)
//        dispatchers = ExecutorDispatchers()
//        useCase = AddComicUseCaseImpl(nativeSource, localSource, dispatchers)
//    }
//
//    @After
//    fun after() {
//        dispatchers.close()
//    }
//
//    /**
//     * Test what happened if provided comic book path is already in the local storage
//     */
//    @Test
//    fun get_already_stored_metadata() {
//        val comicBookUri = mockk<Uri>()
//
//        every { localSource.contains(refEq(comicBookUri),) } returns true
//
//        runBlocking {
//            useCase.addIntoLibrary(comicBookUri,).shouldBeInstanceOf(ComicsMetadataResult.AlreadyOpened::class)
//        }
//
//        verifySequence {
//            localSource.contains(refEq(comicBookUri),)
//        }
//
//        confirmVerified(nativeSource, localSource)
//    }
//
//    /**
//     * Test success logic
//     */
//    @Test
//    fun get_new_success_metadata() {
//        val comicBookUri = mockk<Uri>()
//        val metadata = mockk<ComicsMetadata>()
//        val successResult = ComicsMetadataResult.Success(metadata)
//
//        every { localSource.contains(refEq(comicBookUri),) } returns false
//        every { localSource.put(refEq(metadata)) } returns 100L
//
//        coEvery { nativeSource.getComicsMetadata(refEq(comicBookUri),) } returns successResult
//
//        runBlocking {
//            useCase.addIntoLibrary(comicBookUri,).shouldBe(successResult)
//        }
//
//        coVerifyOrder {
//            localSource.contains(refEq(comicBookUri),)
//            nativeSource.getComicsMetadata(refEq(comicBookUri),)
//            localSource.put(refEq(metadata))
//        }
//
//        confirmVerified(nativeSource, localSource)
//    }
//
//    @Test
//    fun get_new_cancelled_metadata() {
//        other_metadata(ComicsMetadataResult.Cancelled)
//    }
//
//    @Test
//    fun get_new_read_error_metadata() {
//        other_metadata(mockk<ComicsMetadataResult.ContainerReadError>())
//    }
//
//    @Test
//    fun get_new_open_error_metadata() {
//        other_metadata(mockk<ComicsMetadataResult.ContainerOpenError>())
//    }
//
//    @Test
//    fun get_new_no_pages_error_metadata() {
//        other_metadata(mockk<ComicsMetadataResult.NoComicPagesError>())
//    }
//
//    @Test
//    fun get_new_cant_open_page_error_metadata() {
//        other_metadata(mockk<ComicsMetadataResult.CantOpenPageImage>())
//    }
//
//    private fun other_metadata(result: ComicsMetadataResult) {
//        val comicBookUri = mockk<Uri>()
//
//        every { localSource.contains(refEq(comicBookUri),) } returns false
//
//        coEvery { nativeSource.getComicsMetadata(refEq(comicBookUri),) } returns result
//
//        runBlocking {
//            useCase.addIntoLibrary(comicBookUri,).shouldBe(result)
//        }
//
//        coVerifyOrder {
//            localSource.contains(refEq(comicBookUri),)
//            nativeSource.getComicsMetadata(refEq(comicBookUri),)
//        }
//
//        confirmVerified(nativeSource, localSource)
//    }
//}