package com.almadevelop.comixreader.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.almadevelop.comixreader.common.entity.FileHashData
import com.almadevelop.comixreader.data.entity.*
import com.almadevelop.comixreader.data.source.local.db.ComicDatabase
import com.almadevelop.comixreader.data.source.local.db.entity.NewBookPath
import com.almadevelop.comixreader.data.source.local.db.entity.NewBookTitle
import com.almadevelop.comixreader.data.source.local.db.entity.TaggedComicBook
import com.almadevelop.comixreader.data.source.local.db.query.QueryParams
import com.almadevelop.comixreader.data.source.local.db.query.QuerySort
import com.almadevelop.comixreader.data.source.local.db.query.TagFilterType
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeSortedAccordingTo
import org.amshove.kluent.shouldContainAll
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val executor = Executors.newSingleThreadExecutor()

    init {
        AndroidThreeTen.init(context)
    }

    private lateinit var database: ComicDatabase

    @BeforeTest
    fun setup() {
        database = ComicDatabase.instance(context, executor, true)
    }

    @AfterTest
    fun after() {
        ComicDatabase.closeInstance()
    }

    @Test
    fun testComicBookSource() {
        runBlocking {
            val idToBook = Array(50) { ComicBook.new(it, Random.nextBoolean()) }
                .let {
                    val ids = database.comicBookSource().insertOrReplace(*it)

                    assertEquals(
                        it.size,
                        ids.size,
                        "Wrong ids count"
                    )

                    assertEquals(
                        it.size.toLong(),
                        database.comicBookSource().count(),
                        "Wrong count"
                    )

                    ids.zip(it).toMap()
                }

            //check comic book query
            with(database.comicBookSource().querySimpleWithTags()) {
                assertEquals(
                    idToBook.size,
                    size,
                    "Wrong comic books count"
                )

                forEach {
                    val inputBook = idToBook.getValue(it.id)

                    assertEquals(inputBook.coverPosition, it.coverPosition)
                    assertEquals(inputBook.displayName, it.displayName)
                    assertEquals(inputBook.filePath, it.filePath)
                    assertEquals(it.tags.size, 0)
                }
            }

            //check full comic book data
            idToBook.forEach { (id, inputBook) ->
                val fullComicBook = database.comicBookSource().getFullById(id)

                assertNotNull(fullComicBook)

                inputBook.assert(fullComicBook.comicBook)

                assertTrue(fullComicBook.tags.isEmpty())
            }

            //check get comic book path by id
            idToBook.keys.also {
                val paths = database.comicBookSource().pathById(it)
                assertEquals(it.size, paths.size)

                it.forEachIndexed { i, id ->
                    assertEquals(idToBook.getValue(id).filePath, paths[i])

                }
            }

            //check get comic book path by tag ids
            assertEquals(0, database.comicBookSource().pathByTag(setOf(Long.MAX_VALUE)).size)

            //check that comic book doesn't have tag
            assertFalse(database.comicBookSource().hasTag(idToBook.keys.first(), 0)!!)
            assertNull(database.comicBookSource().hasTag(Long.MAX_VALUE, 0))

            //check empty result if doesn't have such comic books
            assertTrue(database.comicBookSource().hasTag(setOf(Long.MAX_VALUE, -100), 0).isEmpty())
            //check not empty result
            idToBook.keys.take(3).also { ids ->
                val r = database.comicBookSource().hasTag(ids.toSet(), 0)

                assertEquals(3, r.size)

                r.forEachIndexed { i, bookHasTag ->
                    assertEquals(ids[i], bookHasTag.id)
                    assertFalse(bookHasTag.hasTag)
                }
            }

            //delete not existed comic books
            assertEquals(0, database.comicBookSource().deleteByTag(setOf(Long.MAX_VALUE, -100)))

            //check comic book path update
            Uri.parse("file://test/new_path.cbz").also {
                val id = idToBook.keys.random()
                database.comicBookSource().updatePath(NewBookPath(id, it))

                val fullComicBook = database.comicBookSource().getFullById(id)
                assertNotNull(fullComicBook)
                assertEquals(it, fullComicBook.comicBook.filePath)
            }

            //check comic book name update
            "Comic book new title".also {
                val id = idToBook.keys.random()
                database.comicBookSource().updateTitle(NewBookTitle(id, it))

                val fullComicBook = database.comicBookSource().getFullById(id)
                assertNotNull(fullComicBook)
                assertEquals(it, fullComicBook.comicBook.displayName)
            }

            checkFindByPathOrContent(idToBook)

            checkOrder()

            checkDelete(idToBook)

            //delete comic books by tags
            assertEquals(0, database.comicBookSource().deleteByTag(setOf(Long.MAX_VALUE, -10)))
        }
    }

    @Test
    fun testComicBookSourceWithTags() {
        runBlocking {
            val idToBook = Array(10) { ComicBook.new(it, Random.nextBoolean()) }
                .let {
                    database.comicBookSource()
                        .insertOrReplace(*it)
                        .zip(it)
                        .toMap()
                }

            val tags = listOf(
                ComicTag(0, "Tag0", 0),
                ComicTag(0, "Tag1", 1),
                ComicTag(0, "Tag2", 0),
                ComicTag(0, "Tag3", 1)
            ).let { tags ->
                val ids = database.comicTagSource()
                    .insertOrReplace(*tags.toTypedArray())

                assertEquals(tags.size, ids.size)

                ids.zip(tags).toMap()
            }

            idToBook.keys
                .random()
                .also { bookId ->
                    database.comicBookSource().addTags(bookId, tags.keys)

                    //exclude comic book by tag
                    database.comicBookSource()
                        .querySimpleWithTags(QueryParams(tagsFilters = mapOf(tags.keys.random() to TagFilterType.Exclude)))
                        .also { assertEquals(idToBook.size - 1, it.size) }

                    //exclude comic book without tags
                    database.comicBookSource()
                        .querySimpleWithTags(QueryParams(tagsFilters = mapOf(tags.keys.random() to TagFilterType.Include)))
                        .also { assertEquals(1, it.size) }

                    //exclude all. Tag doesn't exist
                    database.comicBookSource()
                        .querySimpleWithTags(QueryParams(tagsFilters = mapOf(Long.MAX_VALUE to TagFilterType.Include)))
                        .also { assertTrue(it.isEmpty()) }

                    //Tag doesn't exist. So all comic books will be queried
                    database.comicBookSource()
                        .querySimpleWithTags(QueryParams(tagsFilters = mapOf(Long.MAX_VALUE to TagFilterType.Exclude)))
                        .also { assertEquals(idToBook.size, it.size) }

                    database.comicBookSource()
                        .getFullById(bookId)
                        .also { comicBook ->
                            assertNotNull(comicBook)
                            comicBook.tags.map { it.id } shouldContainAll tags.keys
                        }

                    database.comicBookSource()
                        .hasTag(bookId, tags.keys.random())
                        .also {
                            assertNotNull(it)
                            assertTrue(it)
                        }

                    database.comicBookSource()
                        .hasTag(setOf(bookId, Long.MAX_VALUE, -100), tags.keys.random())
                        .also {
                            assertEquals(1, it.size)
                            assertTrue(it[0].hasTag)
                            assertEquals(bookId, it[0].id)
                        }

                    tags.keys
                        .random()
                        .also { tagId ->
                            database.comicBookSource()
                                .pathByTag(setOf(tagId))[0]
                                .also {
                                    assertEquals(idToBook.getValue(bookId).filePath, it)
                                }

                            database.comicBookSource().removeTags(bookId, setOf(tagId))

                            database.comicBookSource()
                                .hasTag(bookId, tagId)
                                .also {
                                    assertNotNull(it)
                                    assertFalse(it)
                                }
                        }

                    //remove all tags
                    database.comicBookSource().removeTags(bookId, tags.keys)

                    //join tag table should be empty
                    database.query(
                        SupportSQLiteQueryBuilder.builder(TaggedComicBook.TABLE_NAME)
                            .columns(arrayOf(TaggedComicBook.COLUMN_BOOK_ID))
                            .create()
                    ).use { assertEquals(0, it.count) }
                }

            //delete single comic book by tag
            idToBook.keys
                .random()
                .also { bookId ->
                    database.comicBookSource().addTags(bookId, tags.keys)

                    database.comicBookSource().deleteByTag(setOf(tags.keys.random()))

                    assertEquals(idToBook.size.toLong() - 1, database.comicBookSource().count())
                }
        }
    }

    private suspend fun checkFindByPathOrContent(idToBook: Map<Long, ComicBook>) {
        idToBook.keys.random().also { id ->
            val book = idToBook.getValue(id)

            //find by path
            database.comicBookSource()
                .findByPathOrContent(book.filePath, FileHashData(byteArrayOf(), 100))
                .also {
                    assertNotNull(it)
                    assertEquals(FindResult.Type.Path, it.type)
                    assertEquals(id, it.comicBookWithTags.id)
                }

            //find by content
            database.comicBookSource()
                .findByPathOrContent(Uri.EMPTY, FileHashData(book.fileHash, book.fileSize))
                .also {
                    assertNotNull(it)
                    assertEquals(FindResult.Type.Content, it.type)
                    assertEquals(id, it.comicBookWithTags.id)
                }

            //find by path and content
            database.comicBookSource()
                .findByPathOrContent(book.filePath, FileHashData(book.fileHash, book.fileSize))
                .also {
                    assertNotNull(it)
                    assertEquals(FindResult.Type.Path, it.type)
                    assertEquals(id, it.comicBookWithTags.id)
                }

            database.comicBookSource()
                .findByPathOrContent(Uri.EMPTY, FileHashData(book.fileHash, 0))
                .also { assertNull(it) }

            database.comicBookSource()
                .findByPathOrContent(Uri.EMPTY, FileHashData(byteArrayOf(), book.fileSize))
                .also { assertNull(it) }
        }
    }

    private suspend fun checkDelete(idToBook: Map<Long, ComicBook>) {
        //delete some comic books
        idToBook.keys.take(3).also { ids ->
            val metadataQuery = {
                database.query(
                    SupportSQLiteQueryBuilder.builder(ComicRackMetadata.TABLE_NAME)
                        .columns(arrayOf(ComicRackMetadata.COLUMN_ID))
                        .selection(
                            "${ComicRackMetadata.COLUMN_BOOK_ID} IN (${ids.joinToString { "?" }})",
                            ids.toTypedArray()
                        )
                        .create()
                )
            }

            val metadataPagesQuery = { metadataIds: Set<Long> ->
                database.query(
                    SupportSQLiteQueryBuilder.builder(ComicRackPageMetadata.TABLE_NAME)
                        .columns(arrayOf(ComicRackPageMetadata.COLUMN_ID))
                        .selection(
                            "${ComicRackPageMetadata.COLUMN_METADATA_ID} IN (${metadataIds.joinToString { "?" }})",
                            metadataIds.toTypedArray()
                        )
                        .create()
                )
            }

            val bookPagesQuery = {
                database.query(
                    SupportSQLiteQueryBuilder.builder(ComicBookPage.TABLE_NAME)
                        .columns(arrayOf(ComicBookPage.COLUMN_ID))
                        .selection(
                            "${ComicBookPage.COLUMN_BOOK_ID} IN (${ids.joinToString { "?" }})",
                            ids.toTypedArray()
                        )
                        .create()
                )
            }

            val idsFromCursor = { cursor: Cursor ->
                cursor.use {
                    if (it.count > 0) {
                        ArrayList<Long>(it.count).also { metadataIds ->
                            while (it.moveToNext()) {
                                metadataIds += it.getLong(0)
                            }
                        }
                    } else {
                        emptyList<Long>()
                    }
                }
            }

            database.comicBookSource().delete(ids.toSet())

            //check comic book pages
            bookPagesQuery().use { assertEquals(0, it.count) }
            //should does not have any metadata
            metadataQuery().use { assertEquals(0, it.count) }
            //check metadata pages
            metadataPagesQuery(idsFromCursor(metadataQuery()).toSet()).use {
                assertEquals(0, it.count)
            }

            assertEquals(idToBook.size.toLong() - 3, database.comicBookSource().count())
        }
    }

    private suspend fun checkOrder() {
        //some query
        database.comicBookSource()
            .querySimpleWithTags(QueryParams(15, 1, sort = QuerySort.NameDesc))
            .also { queryResult ->
                assertEquals(15, queryResult.size)

                //check sort order
                queryResult.map { it.displayName } shouldBeSortedAccordingTo reverseOrder()
            }

        database.comicBookSource()
            .querySimpleWithTags(QueryParams(sort = QuerySort.NameAsc))
            .also { queryResult ->
                //check sort order
                queryResult.map { it.displayName } shouldBeSortedAccordingTo naturalOrder()
            }

        database.comicBookSource()
            .querySimpleWithTags(QueryParams(sort = QuerySort.OpenTimeDesc))
            .also { queryResult ->
                //check sort order
                queryResult.map { it.actionTime } shouldBeSortedAccordingTo reverseOrder()
            }

        database.comicBookSource()
            .querySimpleWithTags(QueryParams(sort = QuerySort.OpenTimeAsc))
            .also { queryResult ->
                //check sort order
                queryResult.map { it.actionTime } shouldBeSortedAccordingTo naturalOrder()
            }
    }

    private fun ComicBook.assert(other: ComicBook) {
        assertEquals(coverPosition, other.coverPosition)
        assertEquals(fileSize, other.fileSize)
        assertEquals(displayName, other.displayName)
        fileHash shouldBeEqualTo other.fileHash
        assertEquals(fileSize, other.fileSize)
        assertEquals(filePath, other.filePath)
        assertEquals(metadata != null, other.metadata != null)
        assertEquals(pages.size, other.pages.size)

        metadata?.assert(requireNotNull(other.metadata))
    }

    private fun ComicRackMetadata.assert(other: ComicRackMetadata) {
        assertEquals(ageRating, other.ageRating)
        assertEquals(characters, other.characters)
        assertEquals(colorist, other.colorist)
        assertEquals(coverArtist, other.coverArtist)
        assertEquals(editor, other.editor)
        assertEquals(format, other.format)
        assertEquals(genre, other.genre)
        assertEquals(imprint, other.imprint)
        assertEquals(inker, other.inker)
        assertEquals(languageIso, other.languageIso)
        assertEquals(letterer, other.letterer)
        assertEquals(locations, other.locations)
        assertEquals(notes, other.notes)
        assertEquals(penciller, other.penciller)
        assertEquals(publisher, other.publisher)
        assertEquals(series, other.series)
        assertEquals(seriesGroup, other.seriesGroup)
        assertEquals(storyArc, other.storyArc)
        assertEquals(summary, other.summary)
        assertEquals(teams, other.teams)
        assertEquals(title, other.title)
        assertEquals(web, other.web)
        assertEquals(writer, other.writer)
        assertEquals(blackAndWhite, other.blackAndWhite)
        assertEquals(manga, other.manga)
        assertEquals(year, other.year)
        assertEquals(month, other.month)
        assertEquals(day, other.day)
        assertEquals(number, other.number)
        assertEquals(count, other.count)
        assertEquals(volume, other.volume)
        assertEquals(pageCount, other.pageCount)
        assertEquals(pages?.size, other.pages?.size)

        pages?.forEachIndexed { i, page ->
            page.assert(requireNotNull(other.pages)[i])
        }
    }

    private fun ComicRackPageMetadata.assert(other: ComicRackPageMetadata) {
        assertEquals(position, other.position)
        assertEquals(type, other.type)
        assertEquals(size, other.size)
        assertEquals(width, other.width)
        assertEquals(height, other.height)
    }

    private fun ComicBook.Companion.new(index: Int = 0, hasMetadata: Boolean = true): ComicBook {
        val pages = Array(Random.nextInt(1, 11)) {
            ComicBookPage(
                it.toLong(),
                "test_$it",
                Random.nextInt(0, Int.MAX_VALUE),
                Random.nextInt(0, Int.MAX_VALUE)
            )
        }

        return ComicBook(
            "file://test/test${index}.cbz",
            Random.nextLong(0, Long.MAX_VALUE),
            Random.nextBytes(20),
            "test${index}",
            if (pages.size == 1) {
                0
            } else {
                Random.nextLong(0, pages.size.toLong() - 1)
            },
            if (hasMetadata) {
                ComicRackMetadata(
                    optional { "title" },
                    optional { "series" },
                    optional { "summary" },
                    optional { Random.nextInt() },
                    optional { Random.nextInt() },
                    optional { Random.nextInt() },
                    optional { Random.nextInt() },
                    optional { Random.nextInt() },
                    optional { Random.nextInt() },
                    optional { Random.nextInt() },
                    optional { "publisher" },
                    optional { "writer" },
                    optional { "penciller" },
                    optional { "inker" },
                    optional { "colorist" },
                    optional { "letterer" },
                    optional { "coverArtist" },
                    optional { "editor" },
                    optional { "imprint" },
                    optional { "genre" },
                    optional { "format" },
                    optional { "ageRating" },
                    optional { "teams" },
                    optional { "locations" },
                    optional { "storyArc" },
                    optional { "seriesGroup" },
                    optional { Random.nextBoolean() },
                    optional { Random.nextBoolean() },
                    optional { "characters" },
                    optional { "web" },
                    optional { "notes" },
                    optional { "languageIso" },
                    Array(pages.size) {
                        ComicRackPageMetadata(
                            it,
                            optional { "type" },
                            optional { Random.nextLong(0, Long.MAX_VALUE) },
                            optional { Random.nextInt(0, Int.MAX_VALUE) },
                            optional { Random.nextInt(0, Int.MAX_VALUE) }
                        )
                    }
                )
            } else {
                null
            },
            pages
        )
    }

    private inline fun <T : Any> optional(body: () -> T): T? =
        if (Random.nextBoolean()) {
            body()
        } else {
            null
        }
}