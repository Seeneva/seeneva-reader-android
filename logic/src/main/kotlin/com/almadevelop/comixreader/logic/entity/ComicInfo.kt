package com.almadevelop.comixreader.logic.entity

import android.net.Uri
import java.time.temporal.TemporalAccessor

/**
 * Full comic book metadata
 */
data class ComicInfo(
    val id: Long,
    val displayName: CharSequence,
    val path: Uri,
    val hash: String,
    val formattedSize: String, //human formatted file size
    val coverPosition: Long,
    val pagesCount: Int,
    val tagNames: List<String>, //tag names
    val series: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val issue: Int? = null,
    val issuesCount: Int? = null,
    val volume: Int? = null,
    val date: TemporalAccessor? = null,
    val publisher: String? = null,
    val imprint: String? = null,
    val writer: List<String>? = null,
    val penciller: List<String>? = null,
    val inker: List<String>? = null,
    val colorist: List<String>? = null,
    val letterer: List<String>? = null,
    val coverArtist: List<String>? = null,
    val editor: List<String>? = null,
    val genre: List<String>? = null,
    val format: String? = null,
    val ageRating: String? = null,
    val teams: List<String>? = null,
    val locations: List<String>? = null,
    val storyArc: List<String>? = null,
    val seriesGroup: List<String>? = null,
    val blackAndWhite: Boolean? = null,
    val manga: Boolean? = null,
    val characters: List<String>? = null,
    val web: String? = null,
    val languageIso: String? = null,
    val notes: String? = null
)