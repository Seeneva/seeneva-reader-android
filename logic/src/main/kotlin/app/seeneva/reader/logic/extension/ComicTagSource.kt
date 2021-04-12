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

package app.seeneva.reader.logic.extension

import android.content.Context
import app.seeneva.reader.data.entity.ComicTag
import app.seeneva.reader.data.source.local.db.dao.ComicTagSource
import app.seeneva.reader.logic.entity.TagType

/**
 * Check is Tag type hardcoded. Throw exception otherwise
 */
private fun TagType.requireHardcoded() {
    require(hardcoded) { "Incorrect hardcoded tag type: $this" }
}

/**
 * @return hardcoded tag id or null if it wasn't created
 */
internal suspend fun ComicTagSource.getHardcodedTagId(type: TagType): Long? {
    type.requireHardcoded()

    return findByType(type.ordinal)?.id
}

/**
 * @return hardcoded tag or null if it wasn't created
 */
internal suspend fun ComicTagSource.getHardcodedTag(context: Context, type: TagType): ComicTag? {
    type.requireHardcoded()

    //fix tag name for hardcoded types. It allow user to change device locale
    return findByType(type.ordinal)?.let {
        it.copy(name = it.humanName(context))
    }
}

/**
 * Get or create hardcoded tag id by it [type]
 * @param type type of the hardcoded comic book tag
 * @return tag id
 */
internal suspend fun ComicTagSource.getOrCreateHardcodedTagId(type: TagType): Long {
    return getHardcodedTagId(type) ?: insertOrReplace(type.newHardcodedTag()).first()
}

/**
 * Get or create hardcoded tag by it [type]
 * @param type type of the hardcoded comic book tag
 * @return tag
 */
internal suspend fun ComicTagSource.getOrCreateHardcodedTag(
    context: Context,
    type: TagType
): ComicTag {
    return getHardcodedTag(context, type)
        ?: type.newHardcodedTag().let { it.copy(id = insertOrReplace(it).first()) }
}