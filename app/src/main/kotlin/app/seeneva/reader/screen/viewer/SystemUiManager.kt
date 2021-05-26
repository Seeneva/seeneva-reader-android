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

package app.seeneva.reader.screen.viewer

import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import app.seeneva.reader.extension.insetsFlow
import app.seeneva.reader.extension.systemUiVisibilityChange
import app.seeneva.reader.extension.waitLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * States of system UI (status and navigation bars)
 */
enum class SystemUiState {
    /**
     * Status and navigation bars are hidden
     */
    HIDDEN,

    /**
     * Status and navigation bars are showed
     */
    SHOWED,
}

/**
 * Manager of Android system UI (status and navigation bars)
 */
interface SystemUiManager {
    val stateFlow: StateFlow<SystemUiState>

    /**
     * Show specific system ui state
     */
    fun showState(state: SystemUiState)

    /**
     * Hide or show system UI depends on current state
     */
    fun toggle()

    /**
     * Add additional time to showed system UI
     */
    fun holdShown()

    companion object {
        operator fun invoke(
            window: Window,
            lifecycle: Lifecycle,
            initState: SystemUiState = SystemUiState.HIDDEN
        ): SystemUiManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SystemUiManagerInsets(window, lifecycle, initState)
            } else {
                @Suppress("DEPRECATION")
                SystemUiManagerLegacy(
                    window,
                    lifecycle,
                    initState
                )
            }
    }
}

private abstract class BaseSystemUiManager(
    lifecycle: Lifecycle,
) : SystemUiManager {
    protected val coroutineScope = lifecycle.coroutineScope

    private var autoHideJob: Job? = null

    override fun toggle() {
        showState(
            when (stateFlow.value) {
                SystemUiState.SHOWED -> SystemUiState.HIDDEN
                SystemUiState.HIDDEN -> SystemUiState.SHOWED
            }
        )
    }

    override fun holdShown() {
        if (stateFlow.value == SystemUiState.SHOWED) {
            setupAutoHide()
        }
    }

    protected fun onStateChange(state: SystemUiState) {
        if (state == SystemUiState.SHOWED) {
            setupAutoHide()
        } else {
            autoHideJob?.cancel()
            autoHideJob = null
        }
    }

    private fun setupAutoHide() {
        autoHideJob?.cancel()

        autoHideJob = coroutineScope.launch {
            delay(SHOW_DURATION)

            showState(SystemUiState.HIDDEN)
        }
    }

    companion object {
        /**
         * How long show system UI without user interactions
         */
        private const val SHOW_DURATION = 10000L
    }
}

/**
 * System UI manager for Android 11 and higher
 */
private class SystemUiManagerInsets(
    window: Window,
    lifecycle: Lifecycle,
    initState: SystemUiState,
) : BaseSystemUiManager(lifecycle) {
    private val typeMask
        get() = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()

    private val windowInsetsController by lazy {
        checkNotNull(WindowCompat.getInsetsController(window, window.decorView)) {
            "Window inset controller is null"
        }
    }

    override val stateFlow = window.decorView
        .insetsFlow()
        .mapLatest {
            //we need to wait till root view laid out
            //without it windowInsets could be null
            window.decorView.waitLayout()

            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE

            if (it.isVisible(typeMask)) {
                SystemUiState.SHOWED
            } else {
                SystemUiState.HIDDEN
            }
        }
        .stateIn(coroutineScope, SharingStarted.Eagerly, initState)

    init {
        // This is the primary step for ensuring that your app goes edge-to-edge
        // https://developer.android.com/training/gestures/edge-to-edge#lay-out-in-full-screen
        WindowCompat.setDecorFitsSystemWindows(window, false)

        coroutineScope.launch { stateFlow.collect(::onStateChange) }

        showState(initState)
    }

    override fun showState(state: SystemUiState) {
        when (state) {
            SystemUiState.SHOWED -> windowInsetsController.show(typeMask)
            SystemUiState.HIDDEN -> windowInsetsController.hide(typeMask)
        }
    }
}

/**
 * System UI manager for Android lower than 11
 */
@Deprecated("Deprecated since Android 11")
private class SystemUiManagerLegacy(
    window: Window,
    lifecycle: Lifecycle,
    initState: SystemUiState
) : BaseSystemUiManager(lifecycle) {
    private val view = window.decorView

    private val flags = Flags()

    private var initStateRunnable: Runnable? = Runnable { showState(initState) }

    override val stateFlow =
        @Suppress("DEPRECATION")
        view.systemUiVisibilityChange()
            .map {
                if (flags.systemUiHidden(it)) {
                    SystemUiState.HIDDEN
                } else {
                    SystemUiState.SHOWED
                }
            }.stateIn(coroutineScope, SharingStarted.Eagerly, initState)

    init {
        @Suppress("DEPRECATION")
        view.systemUiVisibility = flags.layout

        // This should help to show proper UI state after some events like turn screen ON/OFF
        // Or after hiding/showing the application
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var stateToRestore: SystemUiState? = null

            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                stateToRestore = stateFlow.value
            }

            override fun onResume(owner: LifecycleOwner) {
                super.onResume(owner)

                // restore last state after onPause
                stateToRestore?.also {
                    if (stateFlow.value != it) {
                        showState(it)
                    }
                }

                stateToRestore = null
            }
        })

        coroutineScope.launch { stateFlow.collect(::onStateChange) }

        // If you call `showState(initState)` as is without using `post`
        // OnSystemUiVisibilityChangeListener will not be called after screen rotation
        // Weird...
        view.post(initStateRunnable)
    }

    override fun showState(state: SystemUiState) {
        if (initStateRunnable != null) {
            view.removeCallbacks(initStateRunnable)
            initStateRunnable = null
        }

        view.apply {
            @Suppress("DEPRECATION")
            systemUiVisibility = when (state) {
                SystemUiState.SHOWED -> {
                    systemUiVisibility and flags.hide.inv()
                }
                SystemUiState.HIDDEN -> {
                    systemUiVisibility or flags.hide
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private interface Flags {
        /**
         * Hide system UI flags
         */
        val hide: Int

        /**
         * Layout flags
         */
        val layout: Int

        fun systemUiHidden(flags: Int): Boolean

        companion object {
            operator fun invoke() =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    object : Flags {
                        override val hide = (View.SYSTEM_UI_FLAG_IMMERSIVE
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_FULLSCREEN)

                        override val layout = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

                        override fun systemUiHidden(flags: Int) =
                            flags.hasFlag(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)
                    }
                } else {
                    object : Flags {
                        override val hide = View.SYSTEM_UI_FLAG_LOW_PROFILE
                        override val layout = 0

                        override fun systemUiHidden(flags: Int) = flags.hasFlag(hide)
                    }
                }

            private fun Int.hasFlag(flag: Int): Boolean = this and flag == flag
        }
    }
}