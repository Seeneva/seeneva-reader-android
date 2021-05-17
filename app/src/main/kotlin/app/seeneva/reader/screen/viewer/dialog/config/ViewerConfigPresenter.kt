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

package app.seeneva.reader.screen.viewer.dialog.config

import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.extension.observe
import app.seeneva.reader.logic.entity.configuration.ViewerConfig
import app.seeneva.reader.logic.text.tts.TTS
import app.seeneva.reader.presenter.BasePresenter
import app.seeneva.reader.presenter.Presenter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

sealed interface ChangeTtsEvent {
    object Idle : ChangeTtsEvent
    object Process : ChangeTtsEvent
    data class Result(val result: TTS.InitResult) : ChangeTtsEvent
}

interface ViewerConfigPresenter : Presenter {
    val changeTtsEvents: SharedFlow<ChangeTtsEvent>

    /**
     * User change keep screen on value
     * @param keepScreenOn new value
     */
    fun onKeepScreenOnChange(keepScreenOn: Boolean)

    /**
     * User change TTS setting
     * @param ttsEnabled is TTS enabled
     */
    fun onTtsChange(ttsEnabled: Boolean)

    /**
     * User changed system brightness setting
     * @param checked is system brightness checked
     */
    fun onSystemBrightnessChange(checked: Boolean)

    /**
     * User changed brightness value
     * @param brightness new brightness value
     */
    fun onBrightnessChange(brightness: Float)
}

class ViewerConfigPresenterImpl(
    view: ViewerConfigView,
    dispatchers: Dispatchers,
    _tts: Lazy<TTS>,
    private val viewModel: ViewerConfigViewModel
) : BasePresenter<ViewerConfigView>(view, dispatchers), ViewerConfigPresenter {
    private val tts by _tts

    private var systemBrightnessJob: Job? = null

    private var ttsChangeJob: Job? = null

    private val _changeTtsEvents =
        MutableSharedFlow<ChangeTtsEvent>(0, 1, BufferOverflow.DROP_OLDEST)

    override val changeTtsEvents = _changeTtsEvents.asSharedFlow()

    init {
        viewModel.configState.observe(view) {
            when (it) {
                is ConfigState.Loaded -> {
                    view.showConfig(
                        if (it.config.tts && tts.initAsync().await() != TTS.InitResult.Success) {
                            //show disabled TTS if it is not available
                            it.config.copy(tts = false)
                        } else {
                            it.config
                        }
                    )

                    //start emit system brightness to the view
                    if (it.config.systemBrightness) {
                        observeSystemBrightness()
                    }
                }
                is ConfigState.Loading, is ConfigState.Idle -> {
                    view.onConfigLoading()
                    systemBrightnessJob?.cancel()
                }
            }
        }

        viewModel.loadConfig()
    }

    override fun onKeepScreenOnChange(keepScreenOn: Boolean) {
        currentConfig()?.also {
            if (it.keepScreenOn != keepScreenOn) {
                viewModel.saveConfig(it.copy(keepScreenOn = keepScreenOn))
            }
        }
    }

    override fun onTtsChange(ttsEnabled: Boolean) {
        val previousJob = ttsChangeJob

        ttsChangeJob = presenterScope.launch {
            previousJob?.cancelAndJoin()

            _changeTtsEvents.emit(ChangeTtsEvent.Process)

            val config = currentConfig() ?: viewModel.configState
                .filterIsInstance<ConfigState.Loaded>()
                .first()
                .config

            _changeTtsEvents.emit(when {
                !ttsEnabled -> {
                    viewModel.saveConfig(config.copy(tts = ttsEnabled))

                    ChangeTtsEvent.Result(TTS.InitResult.Success)
                }
                else -> {
                    val initResult = tts.initAsync().let {
                        try {
                            it.await()
                        } finally {
                            it.cancel()
                        }
                    }

                    if (initResult == TTS.InitResult.Success) {
                        ensureActive()
                        viewModel.saveConfig(config.copy(tts = ttsEnabled))
                    }

                    ChangeTtsEvent.Result(initResult)
                }
            })
        }.also { it.invokeOnCompletion { _changeTtsEvents.tryEmit(ChangeTtsEvent.Idle) } }
    }

    override fun onSystemBrightnessChange(checked: Boolean) {
        if (checked) {
            observeSystemBrightness()
            onBrightnessChange(ViewerConfig.SYSTEM_BRIGHTNESS)
        } else {
            systemBrightnessJob?.cancel()
            //set default brightness to the view and save config
            view.showBrightness(DEFAULT_BRIGHTNESS)
            onBrightnessChange(DEFAULT_BRIGHTNESS)
        }
    }

    override fun onBrightnessChange(brightness: Float) {
        currentConfig()?.also {
            if (it.brightness != brightness) {
                viewModel.saveConfig(it.copy(brightness = brightness))
            }
        }
    }

    private fun observeSystemBrightness() {
        systemBrightnessJob = viewModel.systemBrightnessFlow
            .observe(view) { brightness ->
                view.showBrightness(brightness)
            }
    }

    /**
     * Try to get current config
     * @return non null if config is already loaded
     */
    private fun currentConfig() = (viewModel.configState.value as? ConfigState.Loaded)?.config

    companion object {
        private const val DEFAULT_BRIGHTNESS = 0.7f
    }
}