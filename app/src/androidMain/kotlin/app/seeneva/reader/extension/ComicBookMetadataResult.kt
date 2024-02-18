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

package app.seeneva.reader.extension

import android.content.res.Resources
import app.seeneva.reader.R
import app.seeneva.reader.logic.entity.ComicAddResult
import app.seeneva.reader.logic.entity.ComicAddResult.Type as AddType

val ComicAddResult.success: Boolean
    get() = when (type) {
        AddType.Success, AddType.AlreadyOpened -> true
        else -> false
    }

fun ComicAddResult.humanDescription(res: Resources): String {
    val resId = when (type) {
        AddType.ContainerReadError -> R.string.notification_open_metatada_error_read_container
        AddType.NoComicPagesError -> R.string.notification_open_metatada_error_no_pages
        AddType.ContainerUnsupportedError -> R.string.notification_open_metatada_error_open_container_unsupported
        AddType.Success -> R.string.notification_open_metatada_success
        AddType.AlreadyOpened -> R.string.notification_open_metatada_already_opened
    }

    return res.getString(resId)
}

fun ComicAddResult.humanDescriptionShort(res: Resources): String {
    val resId = when (type) {
        AddType.ContainerReadError -> R.string.notification_open_metatada_error_read_container_short
        AddType.NoComicPagesError -> R.string.notification_open_metatada_error_no_pages_short

        AddType.ContainerUnsupportedError -> R.string.notification_open_metatada_error_open_container_unsupported_short

        AddType.Success -> R.string.notification_open_metatada_success_short
        AddType.AlreadyOpened -> R.string.notification_open_metatada_already_opened_short
    }

    return res.getString(
        resId,
        if (data.name.length > DESCR_MAX_COMIC_NAME) {
            "${data.name.take(DESCR_MAX_COMIC_NAME)}\u2026"
        } else {
            data.name
        }
    )
}

private const val DESCR_MAX_COMIC_NAME = 10