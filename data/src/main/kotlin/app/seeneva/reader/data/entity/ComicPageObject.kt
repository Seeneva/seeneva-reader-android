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

package app.seeneva.reader.data.entity

import androidx.annotation.FloatRange
import androidx.annotation.Keep
import androidx.room.*

/**
 * Describes single ML object founded on the comic book page
 *
 * @param id object id
 * @param pageId parent page id
 * @param modelId Unused. But will contain ML model version in future
 * @param classId object type id
 * @param prob object probability [.0, 1.0]
 * @param yMin relative left edge value
 * @param xMin relative top edge value
 * @param yMax relative bottom edge value
 * @param xMax relative right edge value
 */
@Entity(
    tableName = ComicPageObject.TABLE_NAME,
    foreignKeys = [ForeignKey(
        entity = ComicBookPage::class,
        parentColumns = [ComicBookPage.COLUMN_ID],
        childColumns = [ComicPageObject.COLUMN_PAGE_ID],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(
        name = ComicPageObject.INDEX_PAGE_ID,
        value = [ComicPageObject.COLUMN_PAGE_ID],
    )]
)
data class ComicPageObject(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ID)
    val id: Long,
    @ColumnInfo(name = COLUMN_PAGE_ID)
    val pageId: Long,
    @ColumnInfo(name = COLUMN_MODEL_ID, defaultValue = "0")
    val modelId: Long,
    @ColumnInfo(name = COLUMN_CLASS_ID)
    val classId: Long,
    @ColumnInfo(name = COLUMN_PROB)
    @FloatRange(from = 0.0, to = 1.0)
    val prob: Float,
    @ColumnInfo(name = COLUMN_Y_MIN)
    @FloatRange(from = 0.0, to = 1.0)
    val yMin: Float,
    @ColumnInfo(name = COLUMN_X_MIN)
    @FloatRange(from = 0.0, to = 1.0)
    val xMin: Float,
    @ColumnInfo(name = COLUMN_Y_MAX)
    @FloatRange(from = 0.0, to = 1.0)
    val yMax: Float,
    @ColumnInfo(name = COLUMN_X_MAX)
    @FloatRange(from = 0.0, to = 1.0)
    val xMax: Float
) {
    /**
     * Called from a native side
     */
    @Suppress("unused")
    @Keep
    @Ignore
    internal constructor(
        classId: Long,
        prob: Float,
        yMin: Float,
        xMin: Float,
        yMax: Float,
        xMax: Float
    ) : this(
        0,
        0,
        0,
        classId,
        prob.coerceIn(.0f, 1.0f),
        yMin.coerceIn(.0f, 1.0f),
        xMin.coerceIn(.0f, 1.0f),
        yMax.coerceIn(.0f, 1.0f),
        xMax.coerceIn(.0f, 1.0f)
    )

    init {
        // Check data

        val floatRange = (.0f..1.0f)

        require(prob in floatRange) { "Probability should be in [.0, 1.0] range. Was: $prob" }
        require(yMin in floatRange) { "Y min should be in [.0, 1.0] range. Was: $yMin" }
        require(xMin in floatRange) { "X min should be in [.0, 1.0] range. Was: $xMin" }
        require(yMax in floatRange) { "Y max should be in [.0, 1.0] range. Was: $yMax" }
        require(xMax in floatRange) { "X max should be in [.0, 1.0] range. Was: $xMax" }
    }

    companion object {
        internal const val TABLE_NAME = "comic_page_object"

        internal const val COLUMN_ID = "id"
        internal const val COLUMN_PAGE_ID = "page_id"
        internal const val COLUMN_MODEL_ID = "model_id"
        internal const val COLUMN_Y_MIN = "y_min"
        internal const val COLUMN_X_MIN = "x_min"
        internal const val COLUMN_Y_MAX = "y_max"
        internal const val COLUMN_X_MAX = "x_max"
        internal const val COLUMN_PROB = "probability"
        internal const val COLUMN_CLASS_ID = "class_id"

        internal const val INDEX_PAGE_ID = "idx_page_id"
    }
}