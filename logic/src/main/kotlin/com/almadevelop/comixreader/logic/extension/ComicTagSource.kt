package com.almadevelop.comixreader.logic.extension

import android.content.Context
import com.almadevelop.comixreader.data.entity.ComicTag
import com.almadevelop.comixreader.data.source.local.db.dao.ComicTagSource
import com.almadevelop.comixreader.logic.entity.TagType

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