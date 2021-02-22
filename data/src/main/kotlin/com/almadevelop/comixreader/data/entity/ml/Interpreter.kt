package com.almadevelop.comixreader.data.entity.ml

import androidx.annotation.Keep
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Machine Learning Interpreter
 * Should be closed after use
 */
class Interpreter private constructor() : Closeable {
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

        other as Interpreter

        if (ptr != other.ptr) return false

        return true
    }

    override fun hashCode(): Int {
        return ptr.hashCode()
    }

    override fun toString(): String {
        return "Interpreter(ptr=$ptr, closed=$_closed)"
    }

    private companion object {
        private const val NULL_PTR = 0L
    }
}