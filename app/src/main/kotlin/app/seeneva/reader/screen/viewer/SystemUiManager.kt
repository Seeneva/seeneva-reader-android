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

package app.seeneva.reader.screen.viewer

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import app.seeneva.reader.extension.insetsFlow
import app.seeneva.reader.extension.waitLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
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
            SystemUiManagerInsets(window, lifecycle, initState)
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
 * System UI manager. It uses [WindowInsetsControllerCompat]
 */
private class SystemUiManagerInsets(
    window: Window,
    lifecycle: Lifecycle,
    initState: SystemUiState,
) : BaseSystemUiManager(lifecycle) {
    private val typeMask
        get() = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()

    private val windowInsetsController by lazy {
        WindowCompat.getInsetsController(window, window.decorView).also {
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }

    override val stateFlow = window.decorView
        .insetsFlow()
        .mapLatest {
            //we need to wait till root view laid out
            //without it windowInsets could be null
            window.decorView.waitLayout()

            if (it.isVisible(typeMask)) {
                SystemUiState.SHOWED
            } else {
                SystemUiState.HIDDEN
            }
        }
        .stateIn(coroutineScope, SharingStarted.Eagerly, initState)

    init {
        // This is the primary step for ensuring that your app goes edge-to-edge
        // https://developer.android.com/develop/ui/views/layout/edge-to-edge-manually
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