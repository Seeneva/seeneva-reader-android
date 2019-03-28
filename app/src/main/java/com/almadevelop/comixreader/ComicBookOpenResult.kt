package com.almadevelop.comixreader

sealed class ComicBookOpenResult

@Suppress("unused")
object Success : ComicBookOpenResult()

@Suppress("unused")
object Cancelled : ComicBookOpenResult()

@Suppress("unused")
data class ContainerReadError(val description: String) : ComicBookOpenResult()

@Suppress("unused")
data class ContainerOpenError(val kind: Kind, val description: String) : ComicBookOpenResult() {
    enum class Kind {
        /**
         * Known format, but it can't be used as a comic container
         */
        UnsupportedType,
        /**
         * IO error during determine file format by it magic numbers
         */
        MagicIO,
        /**
         * Provided file is not a File. Or can't be opened as File
         */
        NotFile,
        /**
         * Unknown comic container file format
         */
        UnknownFileFormat
    }
}

@Suppress("unused")
data class JNIError(val description: String) : ComicBookOpenResult()

@Suppress("unused")
data class CancellationError(val description: String) : ComicBookOpenResult()

@Suppress("unused")
data class NoComicPagesError(val description: String) : ComicBookOpenResult()

