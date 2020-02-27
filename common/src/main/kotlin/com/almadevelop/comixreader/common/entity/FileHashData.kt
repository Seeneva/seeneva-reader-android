package com.almadevelop.comixreader.common.entity

/**
 * Describes file hash data
 *
 * @param hash comic book file hash
 * @param size comic book file size in bytes
 */
data class FileHashData(val hash: ByteArray, val size: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileHashData

        if (!hash.contentEquals(other.hash)) return false
        if (size != other.size) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hash.contentHashCode()
        result = 31 * result + size.hashCode()
        return result
    }
}