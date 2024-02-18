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

package app.seeneva.reader.logic.entity.query.filter

import app.seeneva.reader.data.source.local.db.query.TagFilterType
import app.seeneva.reader.logic.entity.TagType

interface Filter {
    /**
     * Id of the filter
     */
    val id: String
    /**
     * title of the filter
     */
    val title: String

    /**
     * true if this is dummy filter which doesn't apply any actual filtration
     */
    val none: Boolean
        get() = false
}

internal data class DummyFilter(
    override val id: String,
    override val title: String
) : Filter {
    override val none: Boolean
        get() = true
}

/**
 * Filter by comic book tag type
 */
internal data class TagTypeFilter(
    override val id: String,
    override val title: String,
    val tagType: TagType,
    val filterType: TagFilterType
) : Filter