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

package app.seeneva.reader.logic.image.coil.target

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.view.ViewCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.palette.graphics.Palette
import coil.target.PoolableViewTarget
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.logic.image.entity.DrawablePalette
import app.seeneva.reader.logic.image.target.ImageLoaderTarget
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import app.seeneva.reader.logic.image.target.ImageLoaderTarget.State as ImgState

internal class ViewPaletteTarget<T>(
    override val view: T,
    dispatchers: Dispatchers
) : PoolableViewTarget<T>,
    DefaultLifecycleObserver //Coil use and call methods from inside the library
        where T : View, T : ImageLoaderTarget<DrawablePalette> {

    private var coroutineScope: CoroutineScope? = null

    init {
        fun newCoroutineScope() = CoroutineScope(dispatchers.main)

        if (ViewCompat.isAttachedToWindow(view)) {
            coroutineScope = newCoroutineScope()
        }

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                check(
                    !(coroutineScope?.isActive ?: false)
                ) { "Previous coroutine scope still active" }

                coroutineScope = newCoroutineScope()
            }

            override fun onViewDetachedFromWindow(v: View) {
                coroutineScope?.cancel()

                coroutineScope = null
            }
        })
    }

    override fun onStart(placeholder: Drawable?) {
        view.onImageLoadStateChanged(ImgState.Loading(placeholder))
        // cancel any pending palette coroutine
        coroutineScope().coroutineContext.job.cancelChildren()
    }

    override fun onSuccess(result: Drawable) {
        when (result) {
            is BitmapDrawable -> coroutineScope().loadPaletteDrawable(result)
            else -> throw IllegalArgumentException("Can't calculate palette from '${result.javaClass.name}'")
        }
    }

    override fun onError(error: Drawable?) {
        view.onImageLoadStateChanged(ImgState.Error(error))
    }

    override fun onClear() {
        view.onImageLoadStateChanged(ImgState.Clear)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        coroutineScope?.cancel()
        coroutineScope = null
    }

    /**
     * Throws [IllegalStateException] in case if [coroutineScope] is null
     */
    private fun coroutineScope() =
        checkNotNull(coroutineScope) { "Coroutine scope should be initialized" }

    private suspend fun getPalette(src: Bitmap) =
        suspendCancellableCoroutine<Palette> { cont ->
            val async = Palette.from(src)
                .maximumColorCount(8)
                .addFilter { _, hsl -> hsl[2] < .3f } //take only dark colors
                .generate {
                    when (it) {
                        null -> cont.resumeWithException(Throwable("Can't generate palette"))
                        else -> cont.resume(it)
                    }
                }

            cont.invokeOnCancellation {
                @Suppress("DEPRECATION")
                async.cancel(true)
            }
        }

    private fun CoroutineScope.loadPaletteDrawable(src: BitmapDrawable) {
        launch {
            val palette = getPalette(src.bitmap)

            view.onImageLoadStateChanged(ImgState.Success(DrawablePalette(src, palette)))
        }
    }
}