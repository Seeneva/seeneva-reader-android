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

package app.seeneva.reader.extension

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import app.seeneva.reader.common.coroutines.Dispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.koin.core.context.GlobalContext
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

private val koin
    get() = GlobalContext.get()

private val mainImmediateDispatcher
    get() = koin.get<Dispatchers>().mainImmediate

inline fun <reified T : View?> View.findViewByIdCompat(@IdRes id: Int): T =
    findViewById<T>(id)

/**
 * @see androidx.core.view.doOnLayout
 */
suspend fun View.waitLayout() {
    withContext(mainImmediateDispatcher) {
        if (ViewCompat.isLaidOut(this@waitLayout) && !isLayoutRequested) {
            return@withContext
        } else {
            waitNextLayout()
        }
    }
}

/**
 * @see androidx.core.view.doOnNextLayout
 */
suspend fun View.waitNextLayout() {
    withContext(mainImmediateDispatcher) {
        suspendCancellableCoroutine<Unit> { cont ->
            val listener = object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int
                ) {
                    removeOnLayoutChangeListener(this)
                    cont.resume(Unit)
                }
            }

            addOnLayoutChangeListener(listener)

            cont.invokeOnCancellation { removeOnLayoutChangeListener(listener) }
        }
    }
}

/**
 * Wait till View attach state change
 * @param attached wait for attach or detach state
 */
suspend fun View.waitAttachChange(attached: Boolean = true) {
    if (ViewCompat.isAttachedToWindow(this) == attached) {
        return
    } else {
        withContext(mainImmediateDispatcher) {
            suspendCancellableCoroutine<Unit> { cont ->
                val listener = object : View.OnAttachStateChangeListener {
                    override fun onViewDetachedFromWindow(v: View) {
                        if (!attached) {
                            removeOnAttachStateChangeListener(this)
                            cont.resume(Unit)
                        }
                    }

                    override fun onViewAttachedToWindow(v: View) {
                        if (attached) {
                            removeOnAttachStateChangeListener(this)
                            cont.resume(Unit)
                        }
                    }
                }

                addOnAttachStateChangeListener(listener)

                cont.invokeOnCancellation { removeOnAttachStateChangeListener(listener) }
            }
        }
    }
}

/**
 * It will remove any previously set [View.OnSystemUiVisibilityChangeListener]
 * If you set own [View.OnSystemUiVisibilityChangeListener] after this flow will be cancelled
 *
 * @see View.OnSystemUiVisibilityChangeListener
 */
@Deprecated(message = "Deprecated since Android 11")
fun View.systemUiVisibilityChange(): Flow<Int> =
    @Suppress("DEPRECATION")
    singleListenerFlow<Int, View.OnSystemUiVisibilityChangeListener>(
        initListener = { WeakReference(View.OnSystemUiVisibilityChangeListener { trySend(it) }) },
        applyListener = { v, listener -> v.setOnSystemUiVisibilityChangeListener(listener) }
    )

/**
 * It will remove any previously set [View.OnApplyWindowInsetsListener]
 * If you set own [View.OnApplyWindowInsetsListener] after this flow will be cancelled
 *
 * On Android < 21 it will return empty Flow
 *
 * @see View.OnApplyWindowInsetsListener
 */
fun View.insetsFlow() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        singleListenerFlow<WindowInsetsCompat, OnApplyWindowInsetsListener>(
            initListener = {
                WeakReference(OnApplyWindowInsetsListener { _, insets ->
                    trySend(insets)

                    insets
                })
            },
            applyListener = { v, listener ->
                ViewCompat.setOnApplyWindowInsetsListener(
                    v,
                    listener
                )
            }
        )
    } else {
        emptyFlow()
    }

private inline fun <T, L : Any> View.singleListenerFlow(
    crossinline initListener: ProducerScope<T>.() -> WeakReference<L>,
    crossinline applyListener: (v: View, listener: L?) -> Unit
): Flow<T> =
    callbackFlow {
        val detachListener = object : View.OnAttachStateChangeListener {
            override fun onViewDetachedFromWindow(v: View) {
                close()
            }

            override fun onViewAttachedToWindow(v: View) {}
        }

        //register detach listener to close flow as soon as View removed from Window
        addOnAttachStateChangeListener(detachListener)

        //wait until view attached
        waitAttachChange()

        val weakListener = initListener()

        applyListener(this@singleListenerFlow, requireNotNull(weakListener.get()))

        launch {
            //it is needed to auto cancel this flow if user set another system UI listener
            //maybe I should throw exception here? I'm not sure
            while (isActive) {
                delay(500)
                if (weakListener.get() == null) {
                    close()
                }
            }
        }

        awaitClose {
            removeOnAttachStateChangeListener(detachListener)
            if (weakListener.get() != null) {
                applyListener(this@singleListenerFlow, null)
            }
        }
    }.flowOn(mainImmediateDispatcher).conflate()


/**
 * Apply [OnApplyWindowInsetsListener] and saves initial View's padding
 */
inline fun <reified T : View> T.setOnApplyWindowInsetsListenerByPadding(crossinline listener: (v: T, initialPadding: Rect, insets: WindowInsetsCompat) -> WindowInsetsCompat) {
    val initialPadding = Rect(paddingLeft, paddingTop, paddingRight, paddingBottom)

    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        listener(v as T, initialPadding, insets)
    }
}

/**
 * Set background drawable with preserve padding ability
 * @param background background to set
 */
fun View.setBackgroundPreservePadding(background: Drawable?) {
    var l = paddingLeft
    var t = paddingTop
    var r = paddingRight
    var b = paddingBottom

    if (background != null) {
        val dp = Rect().also { background.getPadding(it) }

        l += dp.left
        t += dp.top
        r += dp.right
        b += dp.bottom
    }

    setBackground(background)

    updatePadding(
        left = l,
        top = t,
        right = r,
        bottom = b
    )
}

fun View.setBackgroundPreservePadding(@DrawableRes backgroundResId: Int) {
    setBackgroundPreservePadding(AppCompatResources.getDrawable(context, backgroundResId))
}