package com.almadevelop.comixreader.data.source.local.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.almadevelop.comixreader.data.entity.ComicBook
import com.almadevelop.comixreader.data.entity.ComicBookPage
import com.almadevelop.comixreader.data.entity.ComicRackMetadata
import com.almadevelop.comixreader.data.entity.ComicTag

/**
 * Result of Room query
 */
data class FullComicBookWithTagsInner(
    @Embedded
    val comicBook: ComicBook,
    @Relation(
        parentColumn = ComicBook.COLUMN_ID,
        entityColumn = ComicRackMetadata.COLUMN_BOOK_ID,
        entity = ComicRackMetadata::class
    )
    val metadataWithPages: ComicRackMetadataWithPages?,
    @Relation(parentColumn = ComicBook.COLUMN_ID, entityColumn = ComicBookPage.COLUMN_BOOK_ID)
    val pages: List<ComicBookPage>,
    @Relation(
        parentColumn = ComicBook.COLUMN_ID,
        entityColumn = ComicTag.COLUMN_ID,
        associateBy = Junction(
            value = TaggedComicBook::class,
            parentColumn = TaggedComicBook.COLUMN_BOOK_ID,
            entityColumn = TaggedComicBook.COLUMN_TAG_ID
        )
    )
    val tags: List<ComicTag>
)

/**
 * Comic book with it tags
 * @param comicBook comic book
 * @param tags comic book tags if any
 */
data class FullComicBookWithTags(val comicBook: ComicBook, val tags: List<ComicTag>)

/**
 * Map inner response to public
 */
internal fun FullComicBookWithTagsInner?.intoPublic(): FullComicBookWithTags? =
    this?.let {
        val comicBook = comicBook.copy(
            metadata = metadataWithPages.intoPublic(),
            pages = pages)
        FullComicBookWithTags(comicBook, tags)
    }