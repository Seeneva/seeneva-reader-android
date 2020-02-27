package com.almadevelop.comixreader.logic

/**
 * Kinds of available representations of the list view
 */
enum class ComicListViewType {
    Grid, List;

    companion object {
        val default: ComicListViewType
            get() = Grid
    }
}