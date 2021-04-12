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

import android.net.Uri

/**
 * Comic book in the list
 *
 * @param id id of the comic book
 * @param title title to display
 * @param path file path of the comic book
 * @param coverPosition cover image file position in the container
 * @param completed is comic book marked as read
 * @param corrupted is comic book can't be opened by the app
 */
data class ComicListItem(
    val id: Long,
    val title: CharSequence,
    val path: Uri,
    val coverPosition: Long,
    val completed: Boolean,
    val corrupted: Boolean
)