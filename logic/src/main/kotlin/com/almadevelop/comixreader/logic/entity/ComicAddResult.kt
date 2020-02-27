package com.almadevelop.comixreader.logic.entity

data class ComicAddResult(val type: Type, val data: FullFileData) {
    enum class Type {
        Success,
        AlreadyOpened,
        ContainerReadError,
        ContainerUnsupportedError, //Opening errors
        ContainerMagicIOError,
        ContainerUnknownFileFormatError,
        NoComicPagesError,
        CantOpenPageImage
    }
}