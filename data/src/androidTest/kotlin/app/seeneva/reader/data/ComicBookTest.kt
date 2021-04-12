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

package app.seeneva.reader.data

import android.database.Cursor
import android.net.Uri
import androidx.core.net.toUri
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.seeneva.reader.data.entity.*
import app.seeneva.reader.common.entity.FileHashData
import app.seeneva.reader.data.source.local.db.ComicDatabase
import app.seeneva.reader.data.source.local.db.entity.TaggedComicBook
import app.seeneva.reader.data.source.local.db.query.QueryParams
import app.seeneva.reader.data.source.local.db.query.QuerySort
import app.seeneva.reader.data.source.local.db.query.TagFilterType
import io.github.serpro69.kfaker.Faker
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.*
import org.junit.AfterClass
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class ComicBookTest {
    private val faker = Faker()

    private val database = ComicDatabase.instance(
        ApplicationProvider.getApplicationContext(),
        Executors.newSingleThreadExecutor(),
        true
    )

    @AfterTest
    fun after() {
        faker.unique.clearAll()
        database.clearAllTables()
    }

    companion object {
        @AfterClass
        @JvmStatic
        fun afterAll() {
            ComicDatabase.closeInstance()
        }
    }

    @Test
    fun testInvalidArgumentFail() {
        assertFailsWith<IllegalArgumentException> { ComicBook.new(shouldFail = true) }
    }

    @Test
    fun testComicBookSource() {
        runBlocking {
            val idToBook = Array(50) { ComicBook.new(it, Random.nextBoolean()) }
                .let { books ->
                    val ids =
                        database.comicBookSource()
                            .insertOrReplace(*books)
                            .shouldHaveSize(books.size)

                    database.comicBookSource().count() shouldBeEqualTo books.size.toLong()

                    ids.zip(books).toMap()
                }

            // check comic book query
            database.comicBookSource()
                .querySimpleWithTags()
                .also { queriedSimpleBooks ->
                    queriedSimpleBooks shouldHaveSize idToBook.size

                    queriedSimpleBooks.forEach { queriedSimpleBook ->
                        val inputBook = idToBook.getValue(queriedSimpleBook.id)

                        queriedSimpleBook.coverPosition shouldBeEqualTo inputBook.coverPosition
                        queriedSimpleBook.displayName shouldBeEqualTo inputBook.displayName
                        queriedSimpleBook.filePath shouldBeEqualTo inputBook.filePath
                        queriedSimpleBook.tags.shouldBeEmpty()
                    }
                }

            //check full comic book data
            idToBook.forEach { (id, inputBook) ->
                val fullComicBook = database.comicBookSource()
                    .getFullById(id)
                    .shouldNotBeNull()

                inputBook.assert(fullComicBook.comicBook)

                assertTrue(fullComicBook.tags.isEmpty())
            }

            //check get comic book path by id
            idToBook.keys.also { ids ->
                val paths = database.comicBookSource()
                    .pathById(ids)
                    .shouldHaveSize(ids.size)

                ids.forEachIndexed { i, id ->
                    paths[i] shouldBeEqualTo idToBook.getValue(id).filePath
                }
            }

            //check get comic book path by tag ids
            database.comicBookSource().pathByTag(setOf(Long.MAX_VALUE)).shouldBeEmpty()

            //check that comic book doesn't have tag
            database.comicBookSource()
                .hasTag(idToBook.keys.first(), 0)
                .shouldNotBeNull()
                .shouldBeFalse()

            // check that database doesn't return anything on unsupported book id
            database.comicBookSource().hasTag(Long.MAX_VALUE, 0).shouldBeNull()

            //check empty result if doesn't have such comic books
            database.comicBookSource().hasTag(setOf(Long.MAX_VALUE, -100), 0).shouldBeEmpty()

            //check not empty result
            idToBook.keys.take(3).also { ids ->
                database.comicBookSource()
                    .hasTag(ids.toSet(), 0)
                    .shouldHaveSize(3)
                    .forEachIndexed { i, bookHasTag ->
                        bookHasTag.id shouldBeEqualTo ids[i]
                        bookHasTag.hasTag.shouldBeFalse()
                    }
            }

            //delete not existed comic books
            database.comicBookSource().deleteByTag(setOf(Long.MAX_VALUE, -100)) shouldBeEqualTo 0

            //check comic book path update
            "file://test/new_path.cbz".toUri().also { uri ->
                val id = idToBook.keys.random()

                database.comicBookSource().updatePath(id, uri)

                val fullComicBook = database.comicBookSource()
                    .getFullById(id)
                    .shouldNotBeNull()

                fullComicBook.comicBook.id shouldBeEqualTo id
                fullComicBook.comicBook.filePath shouldBeEqualTo uri
            }

            //check comic book name update
            "Comic book new title".also { title ->
                val id = idToBook.keys.random()

                database.comicBookSource().updateTitle(id, title)

                val fullComicBook = database.comicBookSource()
                    .getFullById(id)
                    .shouldNotBeNull()

                fullComicBook.comicBook.id shouldBeEqualTo id
                fullComicBook.comicBook.displayName shouldBeEqualTo title
            }

            checkFindByPathOrContent(idToBook)

            checkOrder()

            checkDelete(idToBook)

            //delete comic books by tags
            database.comicBookSource().deleteByTag(setOf(Long.MAX_VALUE, -10)) shouldBeEqualTo 0
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

            val tags = arrayOf(
                ComicTag(0, "Tag0", 0),
                ComicTag(0, "Tag1", 1),
                ComicTag(0, "Tag2", 0),
                ComicTag(0, "Tag3", 1)
            ).let { tags ->
                val ids = database.comicTagSource().insertOrReplace(*tags)

                ids shouldHaveSize tags.size

                ids.zip(tags).toMap()
            }

            idToBook.keys
                .random()
                .also { bookId ->
                    database.comicBookSource().addTags(bookId, tags.keys)

                    //exclude comic book by tag
                    database.comicBookSource()
                        .querySimpleWithTags(QueryParams(tagsFilters = mapOf(tags.keys.random() to TagFilterType.Exclude)))
                        .shouldHaveSize(idToBook.size - 1)

                    //exclude comic book without tags
                    database.comicBookSource()
                        .querySimpleWithTags(QueryParams(tagsFilters = mapOf(tags.keys.random() to TagFilterType.Include)))
                        .shouldHaveSize(1)

                    //exclude all. Tag doesn't exist
                    database.comicBookSource()
                        .querySimpleWithTags(QueryParams(tagsFilters = mapOf(Long.MAX_VALUE to TagFilterType.Include)))
                        .shouldBeEmpty()

                    //Tag doesn't exist. So all comic books will be queried
                    database.comicBookSource()
                        .querySimpleWithTags(QueryParams(tagsFilters = mapOf(Long.MAX_VALUE to TagFilterType.Exclude)))
                        .shouldHaveSize(idToBook.size)

                    database.comicBookSource()
                        .getFullById(bookId)
                        .also { comicBook ->
                            assertNotNull(comicBook)
                            comicBook.tags.map { it.id } shouldContainAll tags.keys
                        }

                    database.comicBookSource()
                        .hasTag(bookId, tags.keys.random())
                        .shouldNotBeNull()
                        .shouldBeTrue()

                    database.comicBookSource()
                        .hasTag(setOf(bookId, Long.MAX_VALUE, -100), tags.keys.random())
                        .shouldHaveSize(1)[0]
                        .also {
                            it.hasTag.shouldBeTrue()
                            it.id shouldBeEqualTo bookId
                        }

                    tags.keys
                        .random()
                        .also { tagId ->
                            with(database.comicBookSource()) {
                                pathByTag(setOf(tagId)).first() shouldBeEqualTo idToBook.getValue(
                                    bookId
                                ).filePath

                                idByTag(setOf(tagId)).first() shouldBeEqualTo bookId

                                removeTags(bookId, setOf(tagId))

                                hasTag(bookId, tagId).shouldNotBeNull().shouldBeFalse()
                            }
                        }

                    //remove all tags
                    database.comicBookSource().removeTags(bookId, tags.keys)

                    //join tag table should be empty
                    database.query(
                        SupportSQLiteQueryBuilder.builder(TaggedComicBook.TABLE_NAME)
                            .columns(arrayOf(TaggedComicBook.COLUMN_BOOK_ID))
                            .create()
                    ).use { it.count shouldBeEqualTo 0 }
                }

            //delete single comic book by tag
            idToBook.keys
                .random()
                .also { bookId ->
                    database.comicBookSource().addTags(bookId, tags.keys)

                    database.comicBookSource().deleteByTag(setOf(tags.keys.random()))

                    database.comicBookSource().count() shouldBeEqualTo idToBook.size.toLong() - 1
                }
        }
    }

    private suspend fun checkFindByPathOrContent(idToBook: Map<Long, ComicBook>) {
        idToBook.keys.random().also { id ->
            val book = idToBook.getValue(id)

            //find by path
            database.comicBookSource()
                .findByContentOrPath(book.filePath, FileHashData(byteArrayOf(), 100))
                .shouldNotBeNull()
                .also { result ->
                    result.type shouldBeEqualTo FindResult.Type.Path
                    result.comicBookWithTags.id shouldBeEqualTo id
                }

            //find by content
            database.comicBookSource()
                .findByContentOrPath(Uri.EMPTY, FileHashData(book.fileHash, book.fileSize))
                .shouldNotBeNull()
                .also { result ->
                    result.type shouldBeEqualTo FindResult.Type.Content
                    result.comicBookWithTags.id shouldBeEqualTo id
                }

            //find by path and content
            database.comicBookSource()
                .findByContentOrPath(book.filePath, FileHashData(book.fileHash, book.fileSize))
                .shouldNotBeNull()
                .also { result ->
                    result.type shouldBeEqualTo FindResult.Type.Content
                    result.comicBookWithTags.id shouldBeEqualTo id
                }

            database.comicBookSource()
                .findByContentOrPath(Uri.EMPTY, FileHashData(book.fileHash, 0))
                .shouldBeNull()

            database.comicBookSource()
                .findByContentOrPath(Uri.EMPTY, FileHashData(byteArrayOf(), book.fileSize))
                .shouldBeNull()
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
                        emptyList()
                    }
                }
            }

            database.comicBookSource().delete(ids.toSet())

            //check comic book pages
            bookPagesQuery().use { it.count shouldBeEqualTo 0 }
            //should does not have any metadata
            metadataQuery().use { it.count shouldBeEqualTo 0 }
            //check metadata pages
            metadataPagesQuery(idsFromCursor(metadataQuery()).toSet()).use {
                it.count shouldBeEqualTo 0
            }

            database.comicBookSource().count() shouldBeEqualTo idToBook.size.toLong() - 3
        }
    }

    private suspend fun checkOrder() {
        //some query
        database.comicBookSource()
            .querySimpleWithTags(QueryParams(15, 1, sort = QuerySort.NameDesc))
            .also { queryResult ->
                queryResult shouldHaveSize 15

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
        other.coverPosition shouldBeEqualTo coverPosition
        other.fileSize shouldBeEqualTo fileSize
        other.displayName shouldBeEqualTo displayName
        other.fileHash shouldBeEqualTo fileHash
        other.fileSize shouldBeEqualTo fileSize
        other.filePath shouldBeEqualTo filePath
        (other.metadata != null).shouldBeEqualTo(metadata != null)
        other.pages shouldHaveSize pages.size

        pages.zip(other.pages).forEach { (p, op) -> p.assert(op) }

        metadata?.assert(requireNotNull(other.metadata))
    }

    private fun ComicBookPage.assert(other: ComicBookPage) {
        other.position shouldBeEqualTo position
        other.name shouldBeEqualTo name
        other.width shouldBeEqualTo width
        other.height shouldBeEqualTo height
        other.objects shouldHaveSize objects.size

        objects.zip(other.objects).forEach { (o, oo) -> o.assert(oo) }
    }

    private fun ComicPageObject.assert(other: ComicPageObject) {
        other.classId shouldBeEqualTo classId
        other.prob shouldBeEqualTo prob
        other.yMin shouldBeEqualTo yMin
        other.xMin shouldBeEqualTo xMin
        other.yMax shouldBeEqualTo yMax
        other.xMax shouldBeEqualTo xMax
    }

    private fun ComicRackMetadata.assert(other: ComicRackMetadata) {
        other.ageRating shouldBeEqualTo ageRating
        other.characters shouldBeEqualTo characters
        other.colorist shouldBeEqualTo colorist
        other.coverArtist shouldBeEqualTo coverArtist
        other.editor shouldBeEqualTo editor
        other.format shouldBeEqualTo format
        other.genre shouldBeEqualTo genre
        other.imprint shouldBeEqualTo imprint
        other.inker shouldBeEqualTo inker
        other.languageIso shouldBeEqualTo languageIso
        other.letterer shouldBeEqualTo letterer
        other.locations shouldBeEqualTo locations
        other.notes shouldBeEqualTo notes
        other.penciller shouldBeEqualTo penciller
        other.publisher shouldBeEqualTo publisher
        other.series shouldBeEqualTo series
        other.seriesGroup shouldBeEqualTo seriesGroup
        other.storyArc shouldBeEqualTo storyArc
        other.summary shouldBeEqualTo summary
        other.teams shouldBeEqualTo teams
        other.title shouldBeEqualTo title
        other.web shouldBeEqualTo web
        other.writer shouldBeEqualTo writer
        other.blackAndWhite shouldBeEqualTo blackAndWhite
        other.manga shouldBeEqualTo manga
        other.year shouldBeEqualTo year
        other.month shouldBeEqualTo month
        other.day shouldBeEqualTo day
        other.number shouldBeEqualTo number
        other.count shouldBeEqualTo count
        other.volume shouldBeEqualTo volume
        other.pageCount shouldBeEqualTo pageCount
        other.pages?.size shouldBeEqualTo pages?.size

        pages?.forEachIndexed { i, page ->
            page.assert(requireNotNull(other.pages)[i])
        }
    }

    private fun ComicRackPageMetadata.assert(other: ComicRackPageMetadata) {
        other.position shouldBeEqualTo position
        other.type shouldBeEqualTo type
        other.size shouldBeEqualTo size
        other.width shouldBeEqualTo width
        other.height shouldBeEqualTo height
    }

    private fun ComicBook.Companion.new(
        index: Int = 0,
        hasMetadata: Boolean = true,
        shouldFail: Boolean = false
    ): ComicBook {
        val title = faker.dcComics.title()

        val pages = Array((1..11).random()) {
            ComicBookPage(
                if (shouldFail && Random.nextBoolean()) {
                    (Long.MIN_VALUE until 0).random()
                } else {
                    it.toLong()
                },
                "${title}_$it",
                if (shouldFail && Random.nextBoolean()) {
                    (Int.MIN_VALUE until 0).random()
                } else {
                    (0..Int.MAX_VALUE).random()
                },
                if (shouldFail) {
                    (Int.MIN_VALUE until 0).random()
                } else {
                    (0..Int.MAX_VALUE).random()
                },
                Array((0..20).random()) {
                    ComicPageObject(
                        Random.nextLong(),
                        Random.nextFloat(),
                        Random.nextFloat(),
                        Random.nextFloat(),
                        Random.nextFloat(),
                        Random.nextFloat()
                    )
                }
            )
        }

        return ComicBook(
            "file://test/test${index}.cbz",
            (0..Long.MAX_VALUE).random(),
            Random.nextBytes(20),
            title,
            if (pages.size == 1) {
                0L
            } else {
                (0L until pages.size).random()
            },
            0,
            if (hasMetadata) {
                ComicRackMetadata(
                    optional { title },
                    optional { faker.movie.title() },
                    optional { faker.movie.quote() },
                    optional { Random.nextInt() },
                    optional { Random.nextInt() },
                    optional { Random.nextInt() },
                    optional { Random.nextInt() },
                    optional { Random.nextInt() },
                    optional { Random.nextInt() },
                    optional { Random.nextInt() },
                    optional { Array((0..5).random()) { faker.name.name() }.joinToString() },
                    optional { Array((0..5).random()) { faker.book.author() }.joinToString() },
                    optional { Array((0..5).random()) { faker.name.name() }.joinToString() },
                    optional { Array((0..5).random()) { faker.name.name() }.joinToString() },
                    optional { Array((0..5).random()) { faker.name.name() }.joinToString() },
                    optional { Array((0..5).random()) { faker.name.name() }.joinToString() },
                    optional { Array((0..5).random()) { faker.name.name() }.joinToString() },
                    optional { Array((0..5).random()) { faker.name.name() }.joinToString() },
                    optional { Array((0..5).random()) { faker.name.name() }.joinToString() },
                    optional { faker.book.genre() },
                    optional { faker.lorem.words() },
                    optional { faker.lorem.words() },
                    optional {
                        arrayOf(
                            faker.dcComics.heroine(),
                            faker.dcComics.villain()
                        ).joinToString()
                    },
                    optional { faker.gameOfThrones.cities() },
                    optional { faker.dcComics.hero() },
                    optional { faker.dcComics.name() },
                    optional { Random.nextBoolean() },
                    optional { Random.nextBoolean() },
                    optional { Array((0..5).random()) { faker.heroes.names() }.joinToString() },
                    optional { "https://example.com" },
                    optional { faker.yoda.quotes() },
                    optional { faker.lorem.supplemental() },
                    Array(pages.size) {
                        ComicRackPageMetadata(
                            it,
                            optional { faker.idNumber.invalid() },
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