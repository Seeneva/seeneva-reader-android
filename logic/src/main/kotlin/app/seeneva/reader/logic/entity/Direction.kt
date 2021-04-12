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

import androidx.annotation.StringRes
import app.seeneva.reader.logic.R

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