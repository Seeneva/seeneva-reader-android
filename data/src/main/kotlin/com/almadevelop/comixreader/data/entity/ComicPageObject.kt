package com.almadevelop.comixreader.data.entity

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