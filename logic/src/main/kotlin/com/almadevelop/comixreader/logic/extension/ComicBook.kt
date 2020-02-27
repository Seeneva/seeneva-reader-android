package com.almadevelop.comixreader.logic.extension

import com.almadevelop.comixreader.data.source.local.db.entity.SimpleComicBookWithTags
import com.almadevelop.comixreader.logic.entity.TagType

internal fun SimpleComicBookWithTags.hasHardcodedTag(tagType: TagType): Boolean =
    tags.find { it.type == tagType.ordinal }?.let { true } ?: false

internal fun SimpleComicBookWithTags.hasTag(id: Long): Boolean =
    tags.find { it.id == id }?.let { true } ?: false