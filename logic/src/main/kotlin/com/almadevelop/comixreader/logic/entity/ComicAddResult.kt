package com.almadevelop.comixreader.logic.entity

/**
 * Result of adding operation
 * @param type result type
 * @param data data of the adding file
 */
data class ComicAddResult(val type: Type, val data: FullFileData) {
    enum class Type {
        Success,
        AlreadyOpened,
        ContainerReadError,
        ContainerUnsupportedError, //Opening errors
        NoComicPagesError,
    }
}