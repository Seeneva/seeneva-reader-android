package com.almadevelop.comixreader.data

import android.content.Context
import android.content.res.AssetManager
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.almadevelop.comixreader.data.source.jni.NativeSource
import com.almadevelop.comixreader.data.source.jni.NativeSourceImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.*

/**
 * Test native comic book opening and parsing
 */
@RunWith(AndroidJUnit4::class)
class NativeComicOpeningTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val assets: AssetManager
        get() = context.assets

    private val nativeSource: NativeSource = NativeSourceImpl(context, TestCoroutineDispatcher())

    private val comicsCacheDir = requireNotNull(context.cacheDir).resolve(COMICS_ROOT_DIR)

    private val sourceOfTruth by lazy {
        val jsonStr = assets.open("$COMICS_ROOT_DIR/truth_source.json")
            .reader()
            .use { it.readText() }

        Json.plain.parseJson(jsonStr).jsonObject
    }

    @BeforeTest
    fun setup() {
        comicsCacheDir.mkdir()
    }

    @AfterTest
    fun after() {
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
            requireNotNull(assets.list(path)).forEach { fileName ->
                val testComicBookPath = "$path/$fileName"

                val targetPath = comicsCacheDir.resolveSibling(testComicBookPath)
                    .also { requireNotNull(it.parentFile).mkdirs() }

                println("Import test comic book '$testComicBookPath' into '$targetPath'")

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

        val fileSourceOfTruth = sourceOfTruth.getObject("files")
            .getObject(initComicName)

        if (fileSourceOfTruth.getPrimitive("pages_count").int <= 0) {
            val ex = assertFailsWith<NativeException> {
                nativeSource.getComicsMetadata(comicFile.toUri(), initComicName)
            }

            assertEquals(NativeException.CODE_EMPTY_BOOK, ex.code, "Wrong exception code")

            return
        }

        with(nativeSource.getComicsMetadata(comicFile.toUri(), initComicName)) {
            println("Comic book was opened! Path: '$comicFile'")

            fileSourceOfTruth.assertEquals(pages.size, "pages_count")

            fileSourceOfTruth.assertEquals(displayName, "name")

            //sorted list received from native code
            pages.sortedBy { it.name }.forEachIndexed { i, v ->
                assertEquals(v, pages[i], "Position $i")
            }

            assertEquals(comicFile.toUri(), filePath, "Wrong path")

            val comicRackSourceOfTruth = if (fileSourceOfTruth.containsKey("comic_rack")) {
                sourceOfTruth.getObject("comic_rack")
                    .getObject(fileSourceOfTruth.getPrimitive("comic_rack").content)
            } else {
                null
            }

            assertEquals(
                comicRackSourceOfTruth != null,
                metadata != null,
                "Wrong comicRack metadata"
            )

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

                    val pagesSourceOfTruth = comicRackSourceOfTruth.getArrayOrNull("pages")

                    assertEquals(pagesSourceOfTruth != null, pages != null)

                    if (pagesSourceOfTruth != null) {
                        requireNotNull(pages).forEachIndexed { i, v ->
                            val pageSourceOfTrue = pagesSourceOfTruth.getObject(i)

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

    private inline fun <reified T> JsonObject.assertEquals(realValue: T?, name: String) {
        @Suppress("IMPLICIT_CAST_TO_ANY")
        val expected = (getPrimitiveOrNull(name)?.let {
            when (T::class) {
                String::class -> it.contentOrNull
                Int::class -> it.intOrNull
                Long::class -> it.longOrNull
                Boolean::class -> it.booleanOrNull
                else -> throw IllegalArgumentException("Unsupported type: '${T::class.java.simpleName}'")
            } as T?
        })

        assertEquals(expected, realValue, "Wrong $name")
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
