package com.almadevelop.comixreader.logic.entity

import androidx.annotation.StringRes
import com.almadevelop.comixreader.logic.R

/**
 * Represent comic book read direction
 * @param id use it to send over data layer sources
 * @param titleResId direction title resource id
 */
enum class Direction(internal val id: Int, @StringRes val titleResId: Int) {
    /**
     * left-to-right
     */
    LTR(0, R.string.comic_script_direction_ltr),

    /**
     * right-to-left
     */
    RTL(1, R.string.comic_script_direction_rtl);

    companion object {
        /**
         * Get direction from [id] argument
         * @param id direction id
         */
        internal fun fromId(id: Int) =
            values().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Unknown direction id: $id")
    }
}