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

package app.seeneva.reader.data.source.local.db.entity

import androidx.room.*
import app.seeneva.reader.data.entity.ComicPageObject

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
