package com.almadevelop.comixreader.logic.entity

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