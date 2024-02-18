/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
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

package app.seeneva.reader.logic.extension

import android.content.Context
import app.seeneva.reader.logic.R
import app.seeneva.reader.logic.entity.TagType
import app.seeneva.reader.data.entity.ComicTag as ComicTagInner

/**
 * @param context
 * @return localized human readable tag's name
 */
internal fun ComicTagInner.humanName(context: Context): String =
    tagType.let {
        when (it) {
            TagType.TYPE_COMPLETED -> context.getString(R.string.comic_tag_completed)
            TagType.TYPE_REMOVED -> context.getString(R.string.comic_tag_removed)
            TagType.TYPE_CORRUPTED -> context.getString(R.string.comic_tag_corrupted)
            else -> {
                require(!it.hardcoded) { "User tag name should be provided for hardcoded type '$type'" }

                it.name
            }
        }
    }

internal val ComicTagInner.tagType: TagType
    get() = try {
        TagType.values()[type]
    } catch (t: Throwable) {
        throw IllegalStateException("Can't get comic tag type: type is not correct. Tag: '$this'")
    }