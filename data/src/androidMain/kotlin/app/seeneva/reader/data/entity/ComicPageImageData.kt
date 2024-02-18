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

package app.seeneva.reader.data.entity

import androidx.annotation.AnyThread
import androidx.annotation.Keep
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Describes single comic book page image
 *
 * @param width image width
 * @param height image height
 */
@Suppress("unused")
class ComicPageImageData @Keep constructor(val width: Int, val height: Int) : Closeable {
    /*
    Previously I was using 'ByteBuffer' here as buffer for encoded image
    But it is much simpler to store pointer to C object and use Mutex on C side
     */

    /**
     * Raw pointer to C allocated encoded comic book page data
     */
    @Keep
    private var dataPtr = NULL_PTR

    private val _closed = AtomicBoolean(false)

    val closed
        get() = _closed.get()

    override fun close() {
        if (_closed.compareAndSet(false, true)) {
            closeNative()
        }
    }

    /**
     * Close this encoded page
     * Native side will use locks and update [dataPtr] properly
     */
    @AnyThread
    private external fun closeNative()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ComicPageImageData

        if (width != other.width) return false
        if (height != other.height) return false
        if (dataPtr != other.dataPtr) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + dataPtr.hashCode()
        return result
    }

    override fun toString(): String {
        return "ComicEncodedPage(width=$width, height=$height, dataPtr=$dataPtr, _closed=$_closed)"
    }

    companion object {
        private const val NULL_PTR = 0L
    }
}