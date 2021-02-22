package com.almadevelop.comixreader.screen.viewer.page.entity

import android.graphics.RectF
import android.net.Uri

/**
 * @param bookPath path to the source comic book
 * @param pagePos source comic book page position
 * @param bbox object bounding box
 */
data class SelectedPageObject(val bookPath: Uri, val pagePos: Long, val bbox: RectF)
