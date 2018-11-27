package com.almadevelop.comixreader

sealed class ComicResult

@Suppress("unused")
object Success : ComicResult()

@Suppress("unused")
object Cancelled : ComicResult()

@Suppress("unused")
data class ContainerReadError(val description: String) : ComicResult()

@Suppress("unused")
data class ContainerOpenError(val kind: Kind, val description: String) : ComicResult() {
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
data class JNIError(val description: String) : ComicResult()

@Suppress("unused")
data class CancellationError(val description: String) : ComicResult()

@Suppress("unused")
data class PreprocessingError(val description: String) : ComicResult()

