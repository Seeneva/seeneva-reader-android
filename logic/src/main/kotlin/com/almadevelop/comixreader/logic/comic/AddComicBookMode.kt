package com.almadevelop.comixreader.logic.comic

/**
 * Modes for adding comic books into user's library
 */
enum class AddComicBookMode {
    /**
     * Move file content into app folder
     */
    Import,
    /**
     * Request persist permissions to the provided content
     */
    Link
}