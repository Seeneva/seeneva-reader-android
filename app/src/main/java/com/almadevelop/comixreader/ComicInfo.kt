package com.almadevelop.comixreader

data class ComicInfo(
    val title: String?,
    val series: String?,
    val summary: String?,
    val number: Int?,
    val count: Int?,
    val year: Int?,
    val month: Int?,
    val writer: String?,
    val publisher: String?,
    val penciller: String?,
    val coverArtist: String?,
    val genre: String?,
    val blackAndWhite: Boolean,
    val manga: Boolean,
    val characters: String?,
    val pageCount: Int?,
    val web: String?,
    val notes: String?,
    val volume: String?,
    val languageIso: String?,
    val pages: Array<ComicInfoPage>?
) {
    data class ComicInfoPage(
        val image: Int?,
        val imageType: String?,
        val imageSize: Long?,
        val imageWidth: Int?,
        val imageHeight: Int?
    )
}