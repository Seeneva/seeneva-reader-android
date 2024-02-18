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

package app.seeneva.reader.logic.entity

import android.net.Uri

/**
 * Describes comic book pages
 *
 * @param id comic book id
 * @param path comic book container path
 * @param name comic book name
 * @param persisted is comic book persisted on the device
 * @param direction comic book read direction
 * @param readPosition current comic book read position (related to view, not comic book container)
 * @param pages comic book pages
 */
data class ComicBookDescription(
    val id: Long,
    val path: Uri,
    val name: String,
    val persisted: Boolean,
    val direction: Direction,
    val readPosition: Int,
    val pages: List<ComicBookPage>
) : Iterable<ComicBookPage> by pages

/**
 * Single comic book page
 *
 * @param id id of the comic book page
 * @param position page position in the comic container
 * @param width comic page width
 * @param height comic page height
 */
data class ComicBookPage(val id: Long, val position: Long, val width: Int, val height: Int)