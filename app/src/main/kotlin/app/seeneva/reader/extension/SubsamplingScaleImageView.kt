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

import android.graphics.PointF
import app.seeneva.reader.R
import app.seeneva.reader.common.coroutines.Dispatchers
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import kotlin.coroutines.resume

private val koin
    get() = GlobalContext.get()

private val mainImmediateDispatcher
    get() = koin.get<Dispatchers>().mainImmediate

private class ComposeOnStateChangedListener private constructor(
    private val listeners: MutableCollection<SubsamplingScaleImageView.OnStateChangedListener>
) : SubsamplingScaleImageView.OnStateChangedListener,
    MutableCollection<SubsamplingScaleImageView.OnStateChangedListener> by listeners {
    override fun onScaleChanged(newScale: Float, origin: Int) {
        listeners.forEach { it.onScaleChanged(newScale, origin) }
    }

    override fun onCenterChanged(newCenter: PointF, origin: Int) {
        listeners.forEach { it.onCenterChanged(newCenter, origin) }
    }

    companion object {
        operator fun invoke() = ComposeOnStateChangedListener(hashSetOf())
    }
}

private class ComposeOnImageEventListener private constructor(
    private val listeners: MutableCollection<SubsamplingScaleImageView.OnImageEventListener>
) : SubsamplingScaleImageView.OnImageEventListener,
    MutableCollection<SubsamplingScaleImageView.OnImageEventListener> by listeners {
    override fun onReady() {
        listeners.forEach { it.onReady() }
    }

    override fun onImageLoaded() {
        listeners.forEach { it.onImageLoaded() }
    }

    override fun onPreviewLoadError(e: Exception) {
        listeners.forEach { it.onPreviewLoadError(e) }
    }

    override fun onImageLoadError(e: Exception) {
        listeners.forEach { it.onImageLoadError(e) }
    }

    override fun onTileLoadError(e: Exception) {
        listeners.forEach { it.onTileLoadError(e) }
    }

    override fun onPreviewReleased() {
        listeners.forEach { it.onPreviewReleased() }
    }

    companion object {
        operator fun invoke() = ComposeOnImageEventListener(hashSetOf())
    }
}

sealed interface SubsamplingStateEvent
data class ScaleChanged(val newScale: Float, val origin: Int) : SubsamplingStateEvent
data class CenterChanged(val newCenter: PointF, val origin: Int) : SubsamplingStateEvent

sealed interface SubsamplingImageEvent {
    object Ready : SubsamplingImageEvent
    object Loaded : SubsamplingImageEvent
    data class PreviewLoadError(val e: Exception) : SubsamplingImageEvent
    data class ImageLoadError(val e: Exception) : SubsamplingImageEvent
    data class TileLoadError(val e: Exception) : SubsamplingImageEvent
    object PreviewReleased : SubsamplingImageEvent
}

enum class SubsamplingAnimationEvent { COMPLETED, INTERRUPTED, INTERRUPTED_BY_USER }

private val SubsamplingScaleImageView.composeStateChangeListener
    get() = getTag(R.id.scale_image_view_state_listener) as? ComposeOnStateChangedListener

private val SubsamplingScaleImageView.composeEventListener
    get() = getTag(R.id.scale_image_view_event_listener) as? ComposeOnImageEventListener

/**
 * Add state change listener. It will remove any listeners which was set using [SubsamplingScaleImageView.setOnStateChangedListener].
 */
fun SubsamplingScaleImageView.addOnStateChangedListener(listener: SubsamplingScaleImageView.OnStateChangedListener) {
    val composeListener = composeStateChangeListener
        ?: ComposeOnStateChangedListener().also {
            setTag(R.id.scale_image_view_state_listener, it)
            setOnStateChangedListener(it)
        }

    composeListener += listener
}

/**
 * Remove state change listener. Do nothing if this [listener] wasn't pass to [addOnStateChangedListener].
 */
fun SubsamplingScaleImageView.removeOnStateChangedListener(listener: SubsamplingScaleImageView.OnStateChangedListener) {
    composeStateChangeListener?.also {
        it -= listener

        if (it.isEmpty()) {
            setTag(R.id.scale_image_view_state_listener, null)
            setOnStateChangedListener(null)
        }
    }
}

fun SubsamplingScaleImageView.addOnImageEventListener(listener: SubsamplingScaleImageView.OnImageEventListener) {
    val composeListener = composeEventListener
        ?: ComposeOnImageEventListener().also {
            setTag(R.id.scale_image_view_event_listener, it)
            setOnImageEventListener(it)
        }

    composeListener += listener
}

fun SubsamplingScaleImageView.removeOnImageEventListener(listener: SubsamplingScaleImageView.OnImageEventListener) {
    composeEventListener?.also {
        it -= listener

        if (it.isEmpty()) {
            setTag(R.id.scale_image_view_event_listener, null)
            setOnImageEventListener(null)
        }
    }
}

/**
 * @return Flow of [SubsamplingScaleImageView.OnStateChangedListener] events
 */
fun SubsamplingScaleImageView.stateChangedFlow() =
    callbackFlow {
        val listener = object : SubsamplingScaleImageView.OnStateChangedListener {
            override fun onScaleChanged(newScale: Float, origin: Int) {
                trySend(ScaleChanged(newScale, origin))
            }

            override fun onCenterChanged(newCenter: PointF, origin: Int) {
                trySend(CenterChanged(newCenter, origin))
            }
        }

        addOnStateChangedListener(listener)

        awaitClose { removeOnStateChangedListener(listener) }
    }.flowOn(mainImmediateDispatcher).conflate()

/**
 * @return  Flow of [SubsamplingScaleImageView.OnImageEventListener] events
 */
fun SubsamplingScaleImageView.imageEventsFlow() =
    callbackFlow {
        val listener = object : SubsamplingScaleImageView.OnImageEventListener {
            override fun onReady() {
                trySend(SubsamplingImageEvent.Ready)
            }

            override fun onImageLoaded() {
                trySend(SubsamplingImageEvent.Loaded)
            }

            override fun onPreviewLoadError(e: Exception) {
                trySend(SubsamplingImageEvent.PreviewLoadError(e))
            }

            override fun onImageLoadError(e: Exception) {
                trySend(SubsamplingImageEvent.ImageLoadError(e))
            }

            override fun onTileLoadError(e: Exception) {
                trySend(SubsamplingImageEvent.TileLoadError(e))
            }

            override fun onPreviewReleased() {
                trySend(SubsamplingImageEvent.PreviewReleased)
            }
        }

        addOnImageEventListener(listener)

        awaitClose { removeOnImageEventListener(listener) }
    }.flowOn(mainImmediateDispatcher).conflate()

suspend fun SubsamplingScaleImageView.animateScaleAndCenterSuspended(
    newScale: Float = scale,
    newCenter: PointF = center!!,
    f: SubsamplingScaleImageView.AnimationBuilder.() -> Unit = {}
) = withContext(mainImmediateDispatcher) {
    suspendCancellableCoroutine<SubsamplingAnimationEvent> { cont ->
        val listener = object : SubsamplingScaleImageView.OnAnimationEventListener {
            override fun onComplete() {
                cont.resume(SubsamplingAnimationEvent.COMPLETED)
            }

            override fun onInterruptedByUser() {
                cont.resume(SubsamplingAnimationEvent.INTERRUPTED_BY_USER)
            }

            override fun onInterruptedByNewAnim() {
                cont.resume(SubsamplingAnimationEvent.INTERRUPTED)
            }
        }

        animateScaleAndCenter(newScale, newCenter)!!.apply(f)
            .withOnAnimationEventListener(listener).start()

        cont.invokeOnCancellation { setScaleAndCenter(scale, center!!) }
    }
}