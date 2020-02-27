package com.almadevelop.comixreader.logic.entity.query.filter

import com.almadevelop.comixreader.data.source.local.db.query.TagFilterType
import com.almadevelop.comixreader.logic.entity.TagType

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