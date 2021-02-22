package com.almadevelop.comixreader.data.source.local.db.entity

import androidx.room.*
import com.almadevelop.comixreader.data.entity.ComicPageObject

/**
 * Describes recognized text on single comic book page object
 * @param id text id
 * @param objectId parent object id
 * @param modelId id of the source ML model
 * @param language text language
 * @param text text on the object
 */
@Entity(
    tableName = ComicPageObjectText.TABLE_NAME,
    foreignKeys = [ForeignKey(
        entity = ComicPageObject::class,
        parentColumns = [ComicPageObject.COLUMN_ID],
        childColumns = [ComicPageObjectText.COLUMN_OBJECT_ID],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(
        name = ComicPageObjectText.INDEX_OBJECT_LANG,
        value = [ComicPageObjectText.COLUMN_OBJECT_ID, ComicPageObjectText.COLUMN_LANGUAGE],
        unique = true
    )]
)
data class ComicPageObjectText(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ID)
    val id: Long = 0,
    @ColumnInfo(name = COLUMN_OBJECT_ID, index = true)
    val objectId: Long,
    @ColumnInfo(name = COLUMN_MODEL_ID, defaultValue = "0")
    val modelId: Long = 0,
    @ColumnInfo(name = COLUMN_LANGUAGE)
    val language: String,
    @ColumnInfo(name = COLUMN_TEXT, defaultValue = "")
    val text: String = "",
) {
    companion object {
        internal const val TABLE_NAME = "comic_page_object_text"

        internal const val COLUMN_ID = "id"
        internal const val COLUMN_OBJECT_ID = "object_id"
        internal const val COLUMN_MODEL_ID = "model_id"
        internal const val COLUMN_LANGUAGE = "language"
        internal const val COLUMN_TEXT = "text"

        internal const val INDEX_OBJECT_LANG = "idx_obj_lang"
    }
}
