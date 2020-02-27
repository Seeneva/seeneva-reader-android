package com.almadevelop.comixreader.logic.entity

import android.net.Uri
import com.almadevelop.comixreader.common.entity.FileHashData

interface SimpleFileData {
    val path: Uri
    val name: String
    val size: Long
}

/**
 * Get file name without extension
 */
val SimpleFileData.nameWithoutExtension: String
    get() = name.substringBeforeLast('.')

/**
 * @param path path to a file (may be content://, file://)
 * @param name name of a file (may contains file extension)
 * @param size file size in bytes
 */
data class FileData(
    override val path: Uri,
    override val name: String,
    override val size: Long
) : SimpleFileData

/**
 * Description of a file received by [path]
 *
 * @param path path to a file (may be content://, file://)
 * @param name name of a file (may contains file extension)
 * @param size file size in bytes
 * @param hash file hash
 */
data class FullFileData(
    override val path: Uri,
    override val name: String,
    override val size: Long,
    val hash: ByteArray
) : SimpleFileData {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FullFileData

        if (path != other.path) return false
        if (name != other.name) return false
        if (size != other.size) return false
        if (!hash.contentEquals(other.hash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + hash.contentHashCode()
        return result
    }
}

/**
 * Convert into [FileHashData] object
 */
internal fun FullFileData.asFileHashData() = FileHashData(hash, size)