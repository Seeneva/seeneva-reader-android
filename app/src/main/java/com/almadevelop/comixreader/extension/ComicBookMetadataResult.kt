package com.almadevelop.comixreader.extension

import android.content.res.Resources
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.logic.entity.ComicAddResult
import com.almadevelop.comixreader.logic.entity.ComicAddResult.Type as AddType

val ComicAddResult.success: Boolean
    get() = when (type) {
        AddType.Success, AddType.AlreadyOpened -> true
        else -> false
    }

fun ComicAddResult.humanDescription(res: Resources): String {
    val resId = when (type) {
        AddType.CantOpenPageImage -> R.string.notification_open_metatada_error_image
        AddType.ContainerReadError -> R.string.notification_open_metatada_error_read_container
        AddType.NoComicPagesError -> R.string.notification_open_metatada_error_no_pages
        AddType.ContainerUnsupportedError -> R.string.notification_open_metatada_error_open_container_unsupported
        AddType.ContainerMagicIOError -> R.string.notification_open_metatada_error_open_container_magic
        AddType.ContainerUnknownFileFormatError -> R.string.notification_open_metatada_error_open_container_unknown
        AddType.Success -> R.string.notification_open_metatada_success
        AddType.AlreadyOpened -> R.string.notification_open_metatada_already_opened
    }

    return res.getString(resId)
}

fun ComicAddResult.humanDescriptionShort(res: Resources): String {
    val resId = when (type) {
        AddType.CantOpenPageImage -> R.string.notification_open_metatada_error_image_short
        AddType.ContainerReadError -> R.string.notification_open_metatada_error_read_container_short
        AddType.NoComicPagesError -> R.string.notification_open_metatada_error_no_pages_short

        AddType.ContainerUnsupportedError -> R.string.notification_open_metatada_error_open_container_unsupported_short
        AddType.ContainerMagicIOError -> R.string.notification_open_metatada_error_open_container_magic_short
        AddType.ContainerUnknownFileFormatError -> R.string.notification_open_metatada_error_open_container_unknown_short

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