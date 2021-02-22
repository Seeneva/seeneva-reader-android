package com.almadevelop.comixreader.screen.viewer.dialog.config

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.provider.Settings
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.entity.configuration.ViewerConfig
import com.almadevelop.comixreader.logic.usecase.ViewerConfigUseCase
import com.almadevelop.comixreader.viewmodel.CoroutineViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class ConfigState {
    data class Loaded(val config: ViewerConfig) : ConfigState()
    object Loading : ConfigState()
    object Idle : ConfigState()
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
        systemBrightnessFlow(context.contentResolver).map { it / 255f }

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
                offer(getSystemBrightness())

                val observer = object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        super.onChange(selfChange)
                        if (!isClosedForSend) {
                            offer(getSystemBrightness())
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