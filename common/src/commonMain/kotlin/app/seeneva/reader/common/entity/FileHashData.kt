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

package app.seeneva.reader.common.entity

/**
 * Describes a comic book file hash data
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
        return size == other.size
    }

    override fun hashCode(): Int {
        var result = hash.contentHashCode()
        result = 31 * result + size.hashCode()
        return result
    }
}