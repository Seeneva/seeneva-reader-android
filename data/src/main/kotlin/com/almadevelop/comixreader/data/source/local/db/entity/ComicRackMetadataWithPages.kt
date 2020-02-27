package com.almadevelop.comixreader.data.source.local.db.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.almadevelop.comixreader.data.entity.ComicRackMetadata
import com.almadevelop.comixreader.data.entity.ComicRackPageMetadata

data class ComicRackMetadataWithPages(
    @Embedded
    val metadata: ComicRackMetadata,
    @Relation(
        parentColumn = ComicRackMetadata.COLUMN_ID,
        entityColumn = ComicRackPageMetadata.COLUMN_METADATA_ID
    )
    val pages: List<ComicRackPageMetadata>?
)

/**
 * Map [ComicRackMetadataWithPages] into [ComicRackMetadata]
 */
internal fun ComicRackMetadataWithPages?.intoPublic(): ComicRackMetadata? =
    this?.let { metadata.copy(pages = pages) }