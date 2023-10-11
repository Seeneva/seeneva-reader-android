/*
 *  This file is part of Seeneva Android Reader
 *  Copyright (C) 2021-2023 Sergei Solodovnikov
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.seeneva.reader.data.entity

import android.net.Uri
import androidx.annotation.Keep
import androidx.room.*
import app.seeneva.reader.data.source.local.db.converters.FindResultTypeIntConverter
import app.seeneva.reader.data.source.local.db.entity.SimpleComicBookWithTags
import java.time.Instant

/**
 * @param filePath path of the comic book of the device
 * @param fileSize comic book size in bytes
 * @param fileHash calculated comic book hash
 * @param displayName displayName of the comic book
 * @param coverPosition position of comics' cover image
 * @param direction comic book pages read direction
 * @param actionTime time in millis when comic book was read
 * @param readPosition last read page position
 */
@Entity(
    tableName = ComicBook.TABLE_NAME,
    indices = [
        Index(
            name = ComicBook.INDEX_FILE,
            value = [ComicBook.COLUMN_FILE_HASH, ComicBook.COLUMN_FILE_SIZE],
            unique = true
        ),
        Index(
            name = ComicBook.INDEX_FILE_PATH,
            value = [ComicBook.COLUMN_FILE_PATH],
            unique = true
        )
    ]
)
data class ComicBook @Ignore constructor(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ID)
    val id: Long,
    @ColumnInfo(name = COLUMN_FILE_PATH, typeAffinity = ColumnInfo.TEXT)
    val filePath: Uri,
    @ColumnInfo(name = COLUMN_FILE_SIZE)
    val fileSize: Long,
    @ColumnInfo(name = COLUMN_FILE_HASH)
    val fileHash: ByteArray,
    @ColumnInfo(name = COLUMN_DISPLAY_NAME)
    val displayName: String,
    @ColumnInfo(name = COLUMN_COVER_POSITION)
    val coverPosition: Long,
    @ColumnInfo(name = COLUMN_DIRECTION)
    val direction: Int,
    @ColumnInfo(name = COLUMN_ACTION_TIME, defaultValue = "(strftime('%s', 'now'))") //UTC seconds
    val actionTime: Instant,
    @ColumnInfo(name = COLUMN_READ_POSITION)
    val readPosition: Long,
    @Ignore
    val metadata: ComicRackMetadata?,
    @Ignore
    val pages: List<ComicBookPage>
) {
    // for some reason @kotlin.jvm.JvmOverloads doesn't work anymore
    // https://issuetracker.google.com/issues/70762008
    /**
     * Used by room
     */
    internal constructor(
        id: Long,
        filePath: Uri,
        fileSize: Long,
        fileHash: ByteArray,
        displayName: String,
        coverPosition: Long,
        direction: Int,
        actionTime: Instant,
        readPosition: Long,
    ) : this(
        id,
        filePath,
        fileSize,
        fileHash,
        displayName,
        coverPosition,
        direction,
        actionTime,
        readPosition,
        null,
        emptyList()
    )

    /**
     * Called from a native side
     */
    @Suppress("unused")
    @Keep
    @Ignore
    internal constructor(
        filePath: String,
        fileSize: Long,
        fileHash: ByteArray,
        displayName: String,
        coverPosition: Long,
        direction: Int,
        metadata: ComicRackMetadata?,
        pages: Array<ComicBookPage>
    ) : this(
        0,
        Uri.parse(filePath),
        fileSize,
        fileHash,
        displayName,
        coverPosition,
        direction,
        Instant.now(),
        coverPosition, //start position on the first page
        metadata,
        pages.asList()
    )

    init {
        require(coverPosition >= 0L) { "Cover position can't be negative" }
        require(readPosition >= 0L) { "Cover position can't be negative" }

        require(fileSize >= 0L) { "File size can't be negative" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ComicBook

        if (id != other.id) return false
        if (filePath != other.filePath) return false
        if (fileSize != other.fileSize) return false
        if (!fileHash.contentEquals(other.fileHash)) return false
        if (displayName != other.displayName) return false
        if (coverPosition != other.coverPosition) return false
        if (direction != other.direction) return false
        if (actionTime != other.actionTime) return false
        if (readPosition != other.readPosition) return false
        if (metadata != other.metadata) return false
        if (pages != other.pages) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + filePath.hashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + fileHash.contentHashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + coverPosition.hashCode()
        result = 31 * result + direction
        result = 31 * result + actionTime.hashCode()
        result = 31 * result + readPosition.hashCode()
        result = 31 * result + (metadata?.hashCode() ?: 0)
        result = 31 * result + pages.hashCode()
        return result
    }

    companion object {
        internal const val TABLE_NAME = "comic_book"

        internal const val COLUMN_ID = "id"
        internal const val COLUMN_FILE_PATH = "file_path"
        internal const val COLUMN_FILE_SIZE = "file_size"
        internal const val COLUMN_FILE_HASH = "file_hash"
        internal const val COLUMN_DISPLAY_NAME = "display_name"
        internal const val COLUMN_COVER_POSITION = "cover_position"
        internal const val COLUMN_DIRECTION = "direction"
        internal const val COLUMN_ACTION_TIME = "action_time"
        internal const val COLUMN_READ_POSITION = "read_position"

        internal const val INDEX_FILE = "idx_file"
        internal const val INDEX_FILE_PATH = "idx_file_path"
    }
}

@TypeConverters(value = [FindResultTypeIntConverter::class])
data class FindResult(
    @ColumnInfo(name = COLUMN_FOUND_TYPE)
    val type: Type,
    @Embedded
    val comicBookWithTags: SimpleComicBookWithTags
) {
    enum class Type(internal val id: Int) {
        /**
         * Was found using file path
         */
        Path(SQL_BY_PATH),

        /**
         * Was found using file content
         */
        Content(SQL_BY_CONTENT)
    }

    companion object {
        internal const val COLUMN_FOUND_TYPE = "found_type"

        //I need these constants to use it in Room annotations
        internal const val SQL_BY_PATH = 0
        internal const val SQL_BY_CONTENT = 1
    }
}