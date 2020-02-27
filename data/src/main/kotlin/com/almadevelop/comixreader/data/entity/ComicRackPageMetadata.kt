package com.almadevelop.comixreader.data.entity

import androidx.annotation.Keep
import androidx.room.*

@Entity(
    tableName = ComicRackPageMetadata.TABLE_NAME,
    foreignKeys = [ForeignKey(
        entity = ComicRackMetadata::class,
        parentColumns = [ComicRackMetadata.COLUMN_ID],
        childColumns = [ComicRackPageMetadata.COLUMN_METADATA_ID],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(
        name = ComicRackPageMetadata.INDEX_METADATA_ID_POSITION,
        value = [ComicRackPageMetadata.COLUMN_METADATA_ID, ComicRackPageMetadata.COLUMN_POSITION],
        unique = true
    )]
)
data class ComicRackPageMetadata(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ID)
    val id: Long = 0,
    @ColumnInfo(name = COLUMN_METADATA_ID)
    val metadataId: Long = 0,
    @ColumnInfo(name = COLUMN_POSITION, defaultValue = "NULL")
    val position: Int? = null,
    @ColumnInfo(name = COLUMN_TYPE, defaultValue = "NULL")
    val type: String? = null,
    @ColumnInfo(name = COLUMN_SIZE, defaultValue = "NULL")
    val size: Long? = null,
    @ColumnInfo(name = COLUMN_WIDTH, defaultValue = "NULL")
    val width: Int? = null,
    @ColumnInfo(name = COLUMN_HEIGHT, defaultValue = "NULL")
    val height: Int? = null
) {
    /**
     * Called from a native side
     */
    @Suppress("unused")
    @Keep
    @Ignore
    internal constructor(
        position: Int?,
        type: String?,
        size: Long?,
        width: Int?,
        height: Int?
    ) : this(0, 0, position, type, size, width, height)

    companion object {
        internal const val TABLE_NAME = "comic_rack_metadata_page"

        internal const val COLUMN_ID = "id"
        internal const val COLUMN_METADATA_ID = "metadata_id"
        internal const val COLUMN_POSITION = "position"
        internal const val COLUMN_TYPE = "type"
        internal const val COLUMN_SIZE = "size"
        internal const val COLUMN_WIDTH = "width"
        internal const val COLUMN_HEIGHT = "height"

        internal const val INDEX_METADATA_ID_POSITION = "idx_metadata_id_position"
    }
}