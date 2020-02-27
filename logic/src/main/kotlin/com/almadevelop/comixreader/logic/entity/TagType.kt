package com.almadevelop.comixreader.logic.entity

import com.almadevelop.comixreader.data.entity.ComicTag

internal enum class TagType {
    /**
     * Tags created by user
     */
    TYPE_USER,
    /**
     * Comic books marked as completed
     */
    TYPE_COMPLETED,
    /**
     * Comic books marked as removed
     */
    TYPE_REMOVED,
    /**
     * Comic books marked as broken (without read access)
     */
    TYPE_CORRUPTED;

    val hardcoded: Boolean
        get() = this != TYPE_USER

    /**
     * Build and return [ComicTag]
     * @return result comic book tag
     */
    fun newHardcodedTag(): ComicTag =
        ComicTag(0, name = name, type = ordinal)
}