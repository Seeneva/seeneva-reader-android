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

package app.seeneva.reader.logic.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import app.seeneva.reader.common.coroutines.Dispatched
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.common.coroutines.io
import coil.bitmap.BitmapPool
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import okhttp3.internal.cache.DiskLruCache
import okio.buffer
import org.tinylog.kotlin.Logger
import java.io.Closeable

internal interface BitmapDiskCache : Closeable {
    /**
     * Try to get Android [Bitmap] from disk cache
     * @param key cache file name
     * @return null cache cannot be found
     */
    suspend fun get(key: String, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap?

    /**
     * Put Android [Bitmap] into disk cache
     * @param key cache file name
     * @param bitmap to put into cache
     */
    suspend fun put(key: String, bitmap: Bitmap)
}

internal class BitmapOkHttpDiskCache(
    private val cache: DiskLruCache,
    _bitmapPool: Lazy<BitmapPool>,
    override val dispatchers: Dispatchers
) : BitmapDiskCache, Dispatched {
    private val mutex = Mutex(false)

    private val bitmapPool by _bitmapPool

    private val defaultCompress
        get() =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                else ->
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
            }

    override suspend fun get(key: String, config: Bitmap.Config): Bitmap? =
        io {
            mutex.withLock {
                cache[key]?.let { snapshot ->
                    snapshot.getSource(0)
                        .buffer()
                        .use {
                            ensureActive()

                            it.readByteArray()
                        }
                }
            }?.let {
                ensureActive()

                val opt = BitmapFactory.Options().apply {
                    inMutable = true
                    inJustDecodeBounds = true
                }

                BitmapFactory.decodeByteArray(it, 0, it.size, opt)

                opt.apply {
                    inJustDecodeBounds = false
                    inPreferredConfig = config
                    inBitmap = bitmapPool.getDirtyOrNull(outWidth, outHeight, config)
                }

                Logger.debug("Decode disk cache using pool: ${opt.inBitmap != null}")

                BitmapFactory.decodeByteArray(it, 0, it.size, opt)
            }
        }

    override suspend fun put(key: String, bitmap: Bitmap) {
        mutex.withLock {
            getEditor(key).commit {
                newSink(0).buffer()
                    .use {
                        bitmap.compress(defaultCompress, 100, it.outputStream())
                    }
            }
        }
    }

    override fun close() {
        cache.close()
    }

    private suspend fun getEditor(key: String): DiskLruCache.Editor =
        io {
            var cacheEditor = cache.edit(key)

            while (cacheEditor == null) {
                yield()
                cacheEditor = cache.edit(key)
            }

            cacheEditor
        }

    private suspend inline fun DiskLruCache.Editor.commit(crossinline body: DiskLruCache.Editor.() -> Unit) {
        io {
            try {
                ensureActive()
                body()
            } finally {
                runCatching { commit() }
                    .onFailure { Logger.error(it, "Can't commit OkHttp cache entry") }
                    .getOrThrow()
            }
        }
    }
}