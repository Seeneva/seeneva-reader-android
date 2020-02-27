package com.almadevelop.comixreader.logic.extension

import android.content.Context
import com.almadevelop.comixreader.logic.R
import com.almadevelop.comixreader.logic.entity.TagType
import com.almadevelop.comixreader.data.entity.ComicTag as ComicTagInner

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