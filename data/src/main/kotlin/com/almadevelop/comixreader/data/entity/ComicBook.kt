package com.almadevelop.comixreader.data.entity

import android.net.Uri
import androidx.annotation.Keep
import androidx.room.*
import com.almadevelop.comixreader.data.source.local.db.entity.SimpleComicBookWithTags
import org.threeten.bp.Instant

/**
 * @param filePath path of the comic book of the device
 * @param fileSize comic book size in bytes
 * @param fileHash calculated comic book hash
 * @param displayName displayName of the comic book
 * @param coverPosition position of comics' cover image
 * @param actionTime time in millis when comic book was read
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
data class ComicBook @JvmOverloads constructor(
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
    @ColumnInfo(name = COLUMN_ACTION_TIME, defaultValue = "(strftime('%s', 'now'))") //UTC seconds
    val actionTime: Instant,
    @Ignore
    val metadata: ComicRackMetadata? = null,
    @Ignore
    val pages: List<ComicBookPage> = emptyList()
) {
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
        metadata: ComicRackMetadata?,
        pages: Array<ComicBookPage>
    ) : this(
        0,
        Uri.parse(filePath),
        fileSize,
        fileHash,
        displayName,
        coverPosition,
        Instant.now(),
        metadata,
        pages.asList()
    )

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
        if (actionTime != other.actionTime) return false
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
        result = 31 * result + actionTime.hashCode()
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
        internal const val COLUMN_ACTION_TIME = "action_time"

        internal const val INDEX_FILE = "idx_file"
        internal const val INDEX_FILE_PATH = "idx_file_path"
    }
}

data class FindResult(
    @ColumnInfo(name = COLUMN_FOUND_TYPE)
    val type: Type,
    @Embedded
    val comicBookWithTags: SimpleComicBookWithTags
) {
    enum class Type {
        /**
         * Was found using file path
         */
        Path,
        /**
         * Was found using file content
         */
        Content
    }

    companion object {
        internal const val COLUMN_FOUND_TYPE = "found_type"

        internal const val SQL_BY_PATH = 0
        internal const val SQL_BY_CONTENT = 1
    }
}