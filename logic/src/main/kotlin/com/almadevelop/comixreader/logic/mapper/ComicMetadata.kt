package com.almadevelop.comixreader.logic.mapper

import android.content.Context
import android.text.format.Formatter
import com.almadevelop.comixreader.data.source.local.db.entity.FullComicBookWithTags
import com.almadevelop.comixreader.data.source.local.db.entity.SimpleComicBookWithTags
import com.almadevelop.comixreader.logic.entity.ComicInfo
import com.almadevelop.comixreader.logic.entity.ComicListItem
import com.almadevelop.comixreader.logic.extension.humanName
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

/**
 * Mapper from [FullComicBookWithTags] into [ComicInfo]
 */
internal typealias ComicMetadataIntoComicInfo = (FullComicBookWithTags?, Context) -> ComicInfo?

/**
 * Mapper from [SimpleComicBookWithTags] into [ComicListItem]
 */
internal typealias ComicMetadataIntoComicListItem = (SimpleComicBookWithTags, Boolean, Boolean) -> ComicListItem

private const val DATE_PATTERN = "yyyy[-M-d][-M]"

internal fun SimpleComicBookWithTags.intoListItem(completed: Boolean, broken: Boolean) =
    ComicListItem(
        id,
        displayName,
        filePath,
        coverPosition,
        completed,
        broken
    )

internal fun FullComicBookWithTags?.intoComicInfo(context: Context): ComicInfo? {
    if (this == null) {
        return null
    }

    val intoDate: () -> TemporalAccessor? = {
        comicBook.metadata?.let { metadata ->
            val year = metadata.year
            val month = metadata.month
            val day = metadata.day

            if (year == null) {
                null
            } else {
                DateTimeFormatter.ofPattern(DATE_PATTERN)
                    .parse(buildString {
                        append(year)
                        if (month != null) {
                            append('-').append(month)
                            if (day != null) {
                                append('-').append(day)
                            }
                        }
                    })
            }
        }
    }

    val stringToList: (String?) -> List<String>? = {
        if (it.isNullOrBlank()) {
            null
        } else {
            it.replace(",", ", ").split(", ")
        }
    }

    //hash is small so it is ok to use this solution
    val hash = comicBook.fileHash.joinToString("") { "%02x".format(it) }

    val formattedSize = Formatter.formatShortFileSize(context, comicBook.fileSize)

    return comicBook.metadata.let { metadata ->
        //count of the comic pages counted by open all images in the container
        val brutePageCounts = comicBook.pages.size

        val tagNames = tags.map { "#${it.humanName(context)}" }.sorted()

        if (metadata == null) {
            ComicInfo(
                comicBook.id,
                comicBook.displayName,
                comicBook.filePath,
                hash,
                formattedSize,
                comicBook.coverPosition,
                brutePageCounts,
                tagNames
            )
        } else {
            ComicInfo(
                comicBook.id,
                comicBook.displayName,
                comicBook.filePath,
                hash,
                formattedSize,
                comicBook.coverPosition,
                metadata.pageCount ?: brutePageCounts,
                tagNames,
                metadata.series,
                metadata.title,
                metadata.summary,
                metadata.number,
                metadata.count,
                metadata.volume,
                intoDate(),
                metadata.publisher,
                metadata.imprint,
                metadata.writer.let(stringToList),
                metadata.penciller.let(stringToList),
                metadata.inker.let(stringToList),
                metadata.colorist.let(stringToList),
                metadata.letterer.let(stringToList),
                metadata.coverArtist.let(stringToList),
                metadata.editor.let(stringToList),
                metadata.genre.let(stringToList),
                metadata.format,
                metadata.ageRating,
                metadata.teams.let(stringToList),
                metadata.locations.let(stringToList),
                metadata.storyArc.let(stringToList),
                metadata.seriesGroup.let(stringToList),
                metadata.blackAndWhite,
                metadata.manga,
                metadata.characters.let(stringToList),
                metadata.web,
                metadata.languageIso,
                metadata.notes
            )
        }
    }
}
