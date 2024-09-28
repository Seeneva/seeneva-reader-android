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

package app.seeneva.reader.logic.entity.ml

/**
 * Represent ML object class supported by the app
 * @param id object class id
 */
enum class ObjectClass(internal val id: Long) {
    /**
     * Single comic book speech balloon
     */
    SPEECH_BALLOON(0),

    /**
     * Sing comic book panel on the page
     */
    PANEL(1);

    companion object {
        /**
         * @param id object class id
         * @return [ObjectClass] if provided class id was valid or null
         */
        internal fun fromId(id: Long) = entries.firstOrNull { it.id == id }

        /**
         * @param id object class id
         * @throws IllegalArgumentException
         */
        internal fun requireFromId(id: Long) =
            requireNotNull(fromId(id)) { "Can't get object class by id: $id" }
    }
}