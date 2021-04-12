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

package app.seeneva.reader.logic

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toRect
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.seeneva.reader.data.NativeException
import app.seeneva.reader.data.entity.ComicBook
import app.seeneva.reader.data.entity.ComicBookPage
import app.seeneva.reader.data.entity.ml.Interpreter
import app.seeneva.reader.data.entity.ml.Tesseract
import app.seeneva.reader.data.source.jni.NativeSource
import app.seeneva.reader.logic.di.TestModules
import app.seeneva.reader.logic.entity.ml.ObjectClass
import app.seeneva.reader.logic.text.Language
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import okio.use
import org.amshove.kluent.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import org.tinylog.kotlin.Logger
import java.io.BufferedReader
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import app.seeneva.reader.data.di.Modules as DataModules

/**
 * Test native comic book opening and parsing
 */
@RunWith(AndroidJUnit4::class)
class NativeComicOpeningTest : KoinTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val assets: AssetManager
        get() = context.assets

    private val nativeSource by inject<NativeSource>()

    /**
     * Machine learning interpreter
     */
    private lateinit var mlInterpreter: Interpreter
    private lateinit var tesseract: Tesseract

    /**
     * Where to copy comic book archives from Android assets
     */
    private val comicsCacheDir = requireNotNull(context.cacheDir).resolve(COMICS_ROOT_DIR)

    private val sourceOfTruth by lazy {
        val jsonStr = assets.open("$COMICS_ROOT_DIR/truth_source.json")
            .bufferedReader()
            .use(BufferedReader::readText)

        Json.decodeFromString<JsonObject>(jsonStr)
    }

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        androidContext(context)
        androidLogger()
        modules(DataModules.nativeModule, TestModules.appModule)
    }


    @BeforeTest
    fun setup() {
        runBlocking {
            mlInterpreter = nativeSource.initInterpreterFromAsset("yolo_seeneva.tflite")
            tesseract = nativeSource.initTesseractFromAsset(
                "${Language.English.code}_seeneva.traineddata",
                Language.English.code
            )
        }

        comicsCacheDir.mkdir()
    }

    @AfterTest
    fun after() {
        mlInterpreter.close()
        tesseract.close()

        comicsCacheDir.deleteRecursively()
    }

    @Test
    fun testZip() {
        process(ComicType.ZIP)
    }

    @Test
    fun test7z() {
        process(ComicType.SZ)
    }

    @Test
    fun testRar() {
        process(ComicType.RAR)
    }

    @Test
    fun testPdf() {
        process(ComicType.PDF)
    }

    private fun process(type: ComicType) {
        val path = "$COMICS_ROOT_DIR/${type.folderName}"

        runBlocking {
            assets.list(path)!!.forEach { fileName ->
                val testComicBookPath = "$path/$fileName"

                val targetPath = comicsCacheDir.resolveSibling(testComicBookPath)
                    .also { requireNotNull(it.parentFile).mkdirs() }

                Logger.info("Import test comic book '$testComicBookPath' into '$targetPath'")

                assets.open(testComicBookPath).use { ins ->
                    targetPath.outputStream().use { fos ->
                        ins.copyTo(fos)
                    }
                }

                openAndAssertComicBookNative(targetPath)
            }
        }
    }

    private suspend fun openAndAssertComicBookNative(comicFile: File) {
        val initComicName = comicFile.nameWithoutExtension
        val initDirection = 0

        val fileSourceOfTruth = sourceOfTruth.getValue("files").jsonObject
            .getValue(initComicName).jsonObject

        if (fileSourceOfTruth.getValue("pages_count").jsonPrimitive.int <= 0) {
            val ex = assertFailsWith<NativeException> {
                nativeSource.getComicsMetadata(
                    comicFile.toUri(),
                    initComicName,
                    initDirection,
                    mlInterpreter
                )
            }

            assertEquals(NativeException.CODE_EMPTY_BOOK, ex.code, "Wrong exception code")

            return
        }

        // Get native calculated file hash and size. This will be truth source
        val (expectedFileHash, expectedFileSize) = nativeSource.getComicFileData(comicFile.toUri())

        with(
            nativeSource.getComicsMetadata(
                comicFile.toUri(),
                initComicName,
                initDirection,
                mlInterpreter
            )
        ) {
            Logger.info("Comic book was opened! Path: '$comicFile'")

            fileSourceOfTruth.assertEquals(pages.size, "pages_count")

            fileSourceOfTruth.assertEquals(displayName, "name")

            // Direction should be same as passed
            direction shouldBeEqualTo initDirection

            // Read position should be the same as cover position
            readPosition shouldBeEqualTo coverPosition shouldBeGreaterOrEqualTo 0L

            // sorted list received from native code
            pages shouldBeSortedAccordingTo compareBy { it.name }

            filePath shouldBeEqualTo comicFile.toUri()

            // Compare size and hash with previously
            fileSize shouldBeEqualTo expectedFileSize shouldBeGreaterThan 0L
            fileHash shouldBeEqualTo expectedFileHash

            val comicRackSourceOfTruth = if (fileSourceOfTruth.containsKey("comic_rack")) {
                metadata.shouldNotBeNull()
                sourceOfTruth.getValue("comic_rack").jsonObject
                    .getValue(fileSourceOfTruth.getValue("comic_rack").jsonPrimitive.content).jsonObject
            } else {
                metadata.shouldBeNull()
                null
            }

            checkPageObjects(pages)

            testOCR(this)

            if (comicRackSourceOfTruth != null) {
                with(requireNotNull(metadata)) {
                    comicRackSourceOfTruth.assertEquals(title, "title")

                    comicRackSourceOfTruth.assertEquals(series, "series")

                    comicRackSourceOfTruth.assertEquals(summary, "summary")

                    comicRackSourceOfTruth.assertEquals(number, "number")

                    comicRackSourceOfTruth.assertEquals(count, "count")

                    comicRackSourceOfTruth.assertEquals(volume, "volume")

                    comicRackSourceOfTruth.assertEquals(pageCount, "page_count")

                    comicRackSourceOfTruth.assertEquals(year, "year")

                    comicRackSourceOfTruth.assertEquals(month, "month")

                    comicRackSourceOfTruth.assertEquals(day, "day")

                    comicRackSourceOfTruth.assertEquals(publisher, "publisher")

                    comicRackSourceOfTruth.assertEquals(writer, "writer")

                    comicRackSourceOfTruth.assertEquals(penciller, "penciller")

                    comicRackSourceOfTruth.assertEquals(inker, "inker")

                    comicRackSourceOfTruth.assertEquals(colorist, "colorist")

                    comicRackSourceOfTruth.assertEquals(letterer, "letterer")

                    comicRackSourceOfTruth.assertEquals(coverArtist, "cover_artist")

                    comicRackSourceOfTruth.assertEquals(editor, "editor")

                    comicRackSourceOfTruth.assertEquals(imprint, "imprint")

                    comicRackSourceOfTruth.assertEquals(genre, "genre")

                    comicRackSourceOfTruth.assertEquals(format, "format")

                    comicRackSourceOfTruth.assertEquals(ageRating, "age_rating")

                    comicRackSourceOfTruth.assertEquals(teams, "teams")

                    comicRackSourceOfTruth.assertEquals(locations, "locations")

                    comicRackSourceOfTruth.assertEquals(storyArc, "story_arc")

                    comicRackSourceOfTruth.assertEquals(seriesGroup, "series_group")

                    comicRackSourceOfTruth.assertEquals(blackAndWhite, "black_and_white")

                    comicRackSourceOfTruth.assertEquals(manga, "manga")

                    comicRackSourceOfTruth.assertEquals(characters, "characters")

                    comicRackSourceOfTruth.assertEquals(web, "web")

                    comicRackSourceOfTruth.assertEquals(notes, "notes")

                    comicRackSourceOfTruth.assertEquals(languageIso, "language_iso")

                    val pagesSourceOfTruth = comicRackSourceOfTruth["pages"]?.jsonArray

                    assertEquals(pagesSourceOfTruth != null, pages != null)

                    if (pagesSourceOfTruth != null) {
                        requireNotNull(pages).forEachIndexed { i, v ->
                            val pageSourceOfTrue = pagesSourceOfTruth[i].jsonObject

                            pageSourceOfTrue.assertEquals(v.position, "position")
                            pageSourceOfTrue.assertEquals(v.type, "type")
                            pageSourceOfTrue.assertEquals(v.size, "image_size")
                            pageSourceOfTrue.assertEquals(v.height, "image_height")
                            pageSourceOfTrue.assertEquals(v.width, "image_width")
                        }
                    }
                }
            }
        }
    }

    /**
     * Check expected page objects on each page
     */
    private fun checkPageObjects(pages: Collection<ComicBookPage>) {
        val expectedObjects = sourceOfTruth.getValue("objects").jsonArray

        pages shouldHaveSize expectedObjects.size

        pages.asSequence().zip(
            expectedObjects.asSequence()
                .map { it.jsonObject }
                .map {
                    mapOf(
                        ObjectClass.PANEL to it.getValue("panels").jsonPrimitive.int,
                        ObjectClass.SPEECH_BALLOON to it.getValue("balloons").jsonPrimitive.int
                    )
                }
        ).forEach { (page, expectedCounts) ->
            val pageObjects = page.objects

            if (expectedCounts.values.sum() == 0) {
                // page doesn't have any objects
                pageObjects.shouldBeEmpty()
            } else {
                pageObjects.shouldNotBeEmpty()

                // ML is about probability. I can't compare objects count. So I will just log results

                pageObjects.asSequence()
                    .map { ObjectClass.requireFromId(it.classId) }
                    .groupingBy { it }
                    .eachCount()
                    .forEach { (oClass, count) ->
                        val expectedCount = expectedCounts.getValue(oClass)
                        if (count == expectedCount) {
                            Logger.info("Wow! $oClass count on page position ${page.position} is correct")
                        } else {
                            Logger.error("$oClass count on page position ${page.position} is $count but should be $expectedCount")
                        }
                    }
            }
        }
    }

    /**
     * Test that OCR api is at least working. OCR model is not so good at the moment
     */
    private suspend fun testOCR(book: ComicBook) {
        var bitmap: Bitmap? = null

        try {
            for (page in book.pages) {
                if (page.objects.isEmpty()) {
                    continue
                }

                nativeSource.getPageImageData(book.filePath, page.position)
                    .use { pageImgData ->
                        for (obj in page.objects) {
                            if (obj.classId != ObjectClass.SPEECH_BALLOON.id) {
                                continue
                            }

                            val objRegion = RectF(
                                pageImgData.width * obj.xMin,
                                pageImgData.height * obj.yMin,
                                pageImgData.width * obj.xMax,
                                pageImgData.height * obj.yMax
                            ).toRect()

                            val b = bitmap?.takeIf {
                                if (it.width == objRegion.width() && it.height == objRegion.height()) {
                                    true
                                } else {
                                    it.recycle()
                                    false
                                }
                            } ?: createBitmap(objRegion.width(), objRegion.height()).also {
                                bitmap = it
                            }

                            nativeSource.decodePage(pageImgData, b, objRegion)

                            val objTxt = nativeSource.recognizeText(tesseract, b)

                            Logger.info("OCR result on page ${page.position}: '$objTxt'")
                        }
                    }
            }
        } finally {
            bitmap?.recycle()
        }
    }

    private inline fun <reified T> JsonObject.assertEquals(realValue: T?, name: String) {
        @Suppress("IMPLICIT_CAST_TO_ANY")
        val expected = (this[name]?.jsonPrimitive?.let {
            when (T::class) {
                String::class -> it.contentOrNull
                Int::class -> it.intOrNull
                Long::class -> it.longOrNull
                Boolean::class -> it.booleanOrNull
                else -> throw IllegalArgumentException("Unsupported type: '${T::class.java.simpleName}'")
            } as T?
        })

        realValue shouldBeEqualTo expected
    }

    private enum class ComicType(val folderName: String) {
        ZIP("zip"),
        RAR("rar"),
        SZ("7z"),
        PDF("pdf")
    }

    private companion object {
        const val COMICS_ROOT_DIR = "comics"
    }
}
