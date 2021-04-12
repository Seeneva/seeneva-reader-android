/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
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

package app.seeneva.reader.data.entity.ml

import androidx.annotation.Keep
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tesseract instance
 * Should be closed after use
 */
class Tesseract @Keep private constructor() : Closeable {
    /**
     * Raw pointer to Tensorflow Lite Interpreter
     */
    @Keep
    private var ptr: Long = NULL_PTR

    private val _closed = AtomicBoolean(false)

    /**
     * Is interpreter was closed
     */
    val closed: Boolean
        get() = _closed.get()

    override fun close() {
        if (_closed.compareAndSet(false, true)) {
            closeNative()
        }
    }

    private external fun closeNative()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Tesseract

        if (ptr != other.ptr) return false

        return true
    }

    override fun hashCode(): Int {
        return ptr.hashCode()
    }

    override fun toString(): String {
        return "Tesseract(ptr=$ptr, closed=$_closed)"
    }



    private companion object {
        private const val NULL_PTR = 0L
    }
}