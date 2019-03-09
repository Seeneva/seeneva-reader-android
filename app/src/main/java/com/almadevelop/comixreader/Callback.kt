package com.almadevelop.comixreader

import java.nio.ByteBuffer


interface Callback {
    val id: Long

    fun onPagesBatchPrepared(input: ByteBuffer): ByteBuffer?
    fun onComicInfoParsed(comicInfo: ComicInfo)
    fun onComicPageObjectsDetected(comicPageObjects: ComicPageObjects)
}