package com.almadevelop.comixreader.logic

/**
 * Kinds of available representations of the list view
 */
enum class ComicListViewType {
    GRID, LIST;

    companion object {
        val default: ComicListViewType
            get() = GRID
    }
}