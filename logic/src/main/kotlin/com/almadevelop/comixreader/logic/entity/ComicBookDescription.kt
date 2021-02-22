package com.almadevelop.comixreader.logic.entity

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