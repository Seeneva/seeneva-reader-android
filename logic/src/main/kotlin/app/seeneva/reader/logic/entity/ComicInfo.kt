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

package app.seeneva.reader.logic.entity

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