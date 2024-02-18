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

package app.seeneva.reader.screen.viewer.dialog.config

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.provider.Settings
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.logic.entity.configuration.ViewerConfig
import app.seeneva.reader.logic.usecase.ViewerConfigUseCase
import app.seeneva.reader.viewmodel.CoroutineViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface ConfigState {
    data class Loaded(val config: ViewerConfig) : ConfigState
    object Loading : ConfigState
    object Idle : ConfigState
}

interface ViewerConfigViewModel {
    /**
     * Emit current system brightness setting (percents value [0;100])
     */
    val systemBrightnessFlow: Flow<Float>

    val configState: StateFlow<ConfigState>

    /**
     * Load viewer configuration
     */
    fun loadConfig()

    fun saveConfig(config: ViewerConfig)
}

class ViewerConfigViewModelImpl(
    context: Context,
    private val configUseCase: ViewerConfigUseCase,
    dispatchers: Dispatchers
) : CoroutineViewModel(dispatchers), ViewerConfigViewModel {
    override val configState = MutableStateFlow<ConfigState>(ConfigState.Idle)

    override val systemBrightnessFlow =
        systemBrightnessFlow(context.contentResolver).map { it / 255.0f }

    private var saveConfigJob: Job? = null

    override fun loadConfig() {
        //do not load configs if we already get them
        if (configState.value is ConfigState.Idle) {
            vmScope.launch {
                configState.value = ConfigState.Loading

                configState.value = try {
                    ConfigState.Loaded(configUseCase.getConfig())
                } catch (t: Throwable) {
                    if (t is CancellationException) {
                        ConfigState.Idle
                    } else {
                        throw t
                    }
                }

            }
        }
    }

    override fun saveConfig(config: ViewerConfig) {
        configUseCase.validate(config)

        val prevSaveConfigJob = saveConfigJob

        saveConfigJob = vmScope.launch {
            prevSaveConfigJob?.cancelAndJoin()

            configState.value = ConfigState.Loaded(config)

            configUseCase.saveConfig(config)
        }
    }

    companion object {
        /**
         * Emit system brightness values [0-255]
         */
        private fun systemBrightnessFlow(contentResolver: ContentResolver) =
            callbackFlow {
                fun getSystemBrightness() =
                    Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)

                //send init value
                trySend(getSystemBrightness())

                val observer = object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        super.onChange(selfChange)
                        if (!isClosedForSend) {
                            trySend(getSystemBrightness())
                        }
                    }

                    override fun deliverSelfNotifications() = false
                }

                contentResolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                    false,
                    observer
                )

                awaitClose { contentResolver.unregisterContentObserver(observer) }

            }.conflate().distinctUntilChanged()
    }
}