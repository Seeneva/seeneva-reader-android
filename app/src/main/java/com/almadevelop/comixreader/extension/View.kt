package com.almadevelop.comixreader.extension

import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.annotation.IdRes
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

inline fun <reified T : View?> View.findViewByIdCompat(@IdRes id: Int): T =
    findViewById<T>(id)

/**
 * @see androidx.core.view.doOnLayout
 */
suspend fun View.waitLayout() {
    withContext(Dispatchers.Main.immediate) {
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
    withContext(Dispatchers.Main.immediate) {
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
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<Unit> { cont ->
                val listener = object : View.OnAttachStateChangeListener {
                    override fun onViewDetachedFromWindow(v: View) {
                        if (!attached) {
                            cont.resume(Unit)
                        }
                    }

                    override fun onViewAttachedToWindow(v: View) {
                        if (attached) {
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
fun View.systemUiVisibilityChange(context: CoroutineContext = Dispatchers.Main.immediate): Flow<Int> =
    @Suppress("DEPRECATION")
    singleListenerFlow<Int, View.OnSystemUiVisibilityChangeListener>(
        context,
        initListener = { WeakReference(View.OnSystemUiVisibilityChangeListener { offer(it) }) },
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
fun View.insetsFlow(context: CoroutineContext = Dispatchers.Main.immediate) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        singleListenerFlow<WindowInsetsCompat, OnApplyWindowInsetsListener>(
            context,
            initListener = {
                WeakReference(OnApplyWindowInsetsListener { _, insets ->
                    offer(insets)

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
    context: CoroutineContext = Dispatchers.Main.immediate,
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
    }.flowOn(context).conflate()


/**
 * Apply [OnApplyWindowInsetsListener] and saves initial View's padding
 */
inline fun <reified T : View> T.setOnApplyWindowInsetsListenerByPadding(crossinline listener: (v: T, initialPadding: Rect, insets: WindowInsetsCompat) -> WindowInsetsCompat) {
    val initialPadding = Rect(paddingLeft, paddingTop, paddingRight, paddingBottom)

    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        listener(v as T, initialPadding, insets)
    }
}

