package com.almadevelop.comixreader

interface Callback {
    val id: Long

    fun onComicBookOpened(pages: Array<ComicPageData>, comicInfo: ComicInfo?)
//    fun onComicPageDataParsed(pageData: ComicPageData)
//    fun onComicInfoParsed(comicInfo: ComicInfo)
}