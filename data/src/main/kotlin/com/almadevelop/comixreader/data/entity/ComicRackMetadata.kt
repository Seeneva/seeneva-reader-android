package com.almadevelop.comixreader.data.entity

import androidx.annotation.Keep
import androidx.room.*

/**
 * @see <a href="https://github.com/ciromattia/kcc/wiki/ComicRack-metadata">ComicRankMetadata</a>
 * @see <a href="https://github.com/dickloraine/EmbedComicMetadata/blob/master/comicinfoxml.py">Parser</a>
 */
@Entity(
    tableName = ComicRackMetadata.TABLE_NAME,
    foreignKeys = [ForeignKey(
        entity = ComicBook::class,
        childColumns = [ComicRackMetadata.COLUMN_BOOK_ID],
        parentColumns = [ComicBook.COLUMN_ID],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(
        name = ComicRackMetadata.INDEX_BOOK_ID,
        value = [ComicRackMetadata.COLUMN_BOOK_ID],
        unique = true
    )]
)
data class ComicRackMetadata @Ignore constructor(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ID)
    val id: Long = 0,
    @ColumnInfo(name = COLUMN_BOOK_ID)
    val bookId: Long = 0,
    @ColumnInfo(name = COLUMN_TITLE, defaultValue = "NULL")
    val title: String? = null,
    @ColumnInfo(name = COLUMN_SERIES, defaultValue = "NULL")
    val series: String? = null,
    @ColumnInfo(name = COLUMN_SUMMARY, defaultValue = "NULL")
    val summary: String? = null,
    @ColumnInfo(name = COLUMN_NUMBER, defaultValue = "NULL")
    val number: Int? = null, //issue
    @ColumnInfo(name = COLUMN_COUNT, defaultValue = "NULL")
    val count: Int? = null, //issue count
    @ColumnInfo(name = COLUMN_VOLUME, defaultValue = "NULL")
    val volume: Int? = null, // Title > Volume > Issue
    @ColumnInfo(name = COLUMN_PAGE_COUNT, defaultValue = "NULL")
    val pageCount: Int? = null,
    @ColumnInfo(name = COLUMN_YEAR, defaultValue = "NULL")
    val year: Int? = null,
    @ColumnInfo(name = COLUMN_MONTH, defaultValue = "NULL")
    val month: Int? = null,
    @ColumnInfo(name = COLUMN_DAY, defaultValue = "NULL")
    val day: Int? = null,
    @ColumnInfo(name = COLUMN_PUBLISHER, defaultValue = "NULL")
    val publisher: String? = null,
    @ColumnInfo(name = COLUMN_WRITER, defaultValue = "NULL")
    val writer: String? = null,
    @ColumnInfo(name = COLUMN_PENCILLER, defaultValue = "NULL")
    val penciller: String? = null,
    @ColumnInfo(name = COLUMN_INKER, defaultValue = "NULL")
    val inker: String? = null,
    @ColumnInfo(name = COLUMN_COLORIST, defaultValue = "NULL")
    val colorist: String? = null,
    @ColumnInfo(name = COLUMN_LETTERER, defaultValue = "NULL")
    val letterer: String? = null,
    @ColumnInfo(name = COLUMN_COVER_ARTIST, defaultValue = "NULL")
    val coverArtist: String? = null,
    @ColumnInfo(name = COLUMN_EDITOR, defaultValue = "NULL")
    val editor: String? = null,
    @ColumnInfo(name = COLUMN_IMPRINT, defaultValue = "NULL")
    val imprint: String? = null,
    @ColumnInfo(name = COLUMN_GENRE, defaultValue = "NULL")
    val genre: String? = null,
    @ColumnInfo(name = COLUMN_FORMAT, defaultValue = "NULL")
    val format: String? = null,
    @ColumnInfo(name = COLUMN_AGE_RATING, defaultValue = "NULL")
    val ageRating: String? = null,
    @ColumnInfo(name = COLUMN_TEAMS, defaultValue = "NULL")
    val teams: String? = null,
    @ColumnInfo(name = COLUMN_LOCATIONS, defaultValue = "NULL")
    val locations: String? = null,
    @ColumnInfo(name = COLUMN_STORY_ARC, defaultValue = "NULL")
    val storyArc: String? = null,
    @ColumnInfo(name = COLUMN_SERIES_GROUP, defaultValue = "NULL")
    val seriesGroup: String? = null,
    @ColumnInfo(name = COLUMN_BLACK_WHITE, defaultValue = "NULL")
    val blackAndWhite: Boolean? = null,
    @ColumnInfo(name = COLUMN_MANGA, defaultValue = "NULL")
    val manga: Boolean? = null,
    @ColumnInfo(name = COLUMN_CHARACTERS, defaultValue = "NULL")
    val characters: String? = null,
    @ColumnInfo(name = COLUMN_WEB, defaultValue = "NULL")
    val web: String? = null,
    @ColumnInfo(name = COLUMN_NOTES, defaultValue = "NULL")
    val notes: String? = null,
    @ColumnInfo(name = COLUMN_LANGUAGE, defaultValue = "NULL")
    val languageIso: String? = null,
    @Ignore
    val pages: List<ComicRackPageMetadata>? = null
) {
    /**
     * Used by Room
     */
    @Suppress("unused")
    @Keep
    internal constructor(
        id: Long,
        bookId: Long,
        title: String?,
        series: String?,
        summary: String?,
        number: Int?,
        count: Int?,
        volume: Int?,
        pageCount: Int?,
        year: Int?,
        month: Int?,
        day: Int?,
        publisher: String?,
        writer: String?,
        penciller: String?,
        inker: String?,
        colorist: String?,
        letterer: String?,
        coverArtist: String?,
        editor: String?,
        imprint: String?,
        genre: String?,
        format: String?,
        ageRating: String?,
        teams: String?,
        locations: String?,
        storyArc: String?,
        seriesGroup: String?,
        blackAndWhite: Boolean?,
        manga: Boolean?,
        characters: String?,
        web: String?,
        notes: String?,
        languageIso: String?
    ) : this(
        id,
        bookId,
        title,
        series,
        summary,
        number,
        count,
        volume,
        pageCount,
        year,
        month,
        day,
        publisher,
        writer,
        penciller,
        inker,
        colorist,
        letterer,
        coverArtist,
        editor,
        imprint,
        genre,
        format,
        ageRating,
        teams,
        locations,
        storyArc,
        seriesGroup,
        blackAndWhite,
        manga,
        characters,
        web,
        notes,
        languageIso,
        null
    )

    /**
     * Called from a native side
     */
    @Suppress("unused")
    @Keep
    @Ignore
    internal constructor(
        title: String?,
        series: String?,
        summary: String?,
        number: Int?,
        count: Int?,
        volume: Int?,
        pageCount: Int?,
        year: Int?,
        month: Int?,
        day: Int?,
        publisher: String?,
        writer: String?,
        penciller: String?,
        inker: String?,
        colorist: String?,
        letterer: String?,
        coverArtist: String?,
        editor: String?,
        imprint: String?,
        genre: String?,
        format: String?,
        ageRating: String?,
        teams: String?,
        locations: String?,
        storyArc: String?,
        seriesGroup: String?,
        blackAndWhite: Boolean?,
        manga: Boolean?,
        characters: String?,
        web: String?,
        notes: String?,
        languageIso: String?,
        pages: Array<ComicRackPageMetadata>?
    ) : this(
        0,
        0,
        title,
        series,
        summary,
        number,
        count,
        volume,
        pageCount,
        year,
        month,
        day,
        publisher,
        writer,
        penciller,
        inker,
        colorist,
        letterer,
        coverArtist,
        editor,
        imprint,
        genre,
        format,
        ageRating,
        teams,
        locations,
        storyArc,
        seriesGroup,
        blackAndWhite,
        manga,
        characters,
        web,
        notes,
        languageIso,
        pages?.asList()
    )

    companion object {
        internal const val TABLE_NAME = "comic_rack_metadata"

        internal const val COLUMN_ID = "id"
        internal const val COLUMN_BOOK_ID = "book_id"
        internal const val COLUMN_TITLE = "title"
        internal const val COLUMN_SERIES = "series"
        internal const val COLUMN_SUMMARY = "summary"
        internal const val COLUMN_NUMBER = "number"
        internal const val COLUMN_COUNT = "count"
        internal const val COLUMN_VOLUME = "volume"
        internal const val COLUMN_PAGE_COUNT = "page_count"
        internal const val COLUMN_YEAR = "year"
        internal const val COLUMN_MONTH = "month"
        internal const val COLUMN_DAY = "day"
        internal const val COLUMN_PUBLISHER = "publisher"
        internal const val COLUMN_WRITER = "writer"
        internal const val COLUMN_PENCILLER = "penciller"
        internal const val COLUMN_INKER = "inker"
        internal const val COLUMN_COLORIST = "colorist"
        internal const val COLUMN_LETTERER = "letterer"
        internal const val COLUMN_COVER_ARTIST = "cover_artist"
        internal const val COLUMN_EDITOR = "editor"
        internal const val COLUMN_IMPRINT = "imprint"
        internal const val COLUMN_GENRE = "genre"
        internal const val COLUMN_FORMAT = "format"
        internal const val COLUMN_AGE_RATING = "age_rating"
        internal const val COLUMN_TEAMS = "teams"
        internal const val COLUMN_LOCATIONS = "locations"
        internal const val COLUMN_STORY_ARC = "story_arc"
        internal const val COLUMN_SERIES_GROUP = "series_group"
        internal const val COLUMN_BLACK_WHITE = "black_white"
        internal const val COLUMN_MANGA = "manga"
        internal const val COLUMN_CHARACTERS = "characters"
        internal const val COLUMN_WEB = "web"
        internal const val COLUMN_NOTES = "notes"
        internal const val COLUMN_LANGUAGE = "language"

        internal const val INDEX_BOOK_ID = "idx_book_id"
    }
}