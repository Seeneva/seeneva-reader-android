/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.seeneva.reader.logic.entity

import android.net.Uri
import app.seeneva.reader.common.entity.FileHashData

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