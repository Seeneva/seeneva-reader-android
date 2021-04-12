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

package app.seeneva.reader.logic.usecase

import app.seeneva.reader.logic.ComicsSettings
import app.seeneva.reader.logic.entity.configuration.ViewerConfig
import kotlinx.coroutines.flow.*
import org.tinylog.kotlin.Logger

interface ViewerConfigUseCase {
    /**
     * Validate viewer config
     * @throws IllegalArgumentException in case of error
     */
    fun validate(config: ViewerConfig)

    /**
     * Get last used comic book viewer config or create a new one
     * @return last used config
     */
    suspend fun getConfig(): ViewerConfig

    /**
     * Save viewer config
     */
    suspend fun saveConfig(config: ViewerConfig)

    /**
     * Get [ViewerConfig] flow. It will emit configs as soon as they will be saved
     */
    fun configFlow(): Flow<ViewerConfig>
}

internal class ViewerConfigUseCaseImpl(private val settings: ComicsSettings) : ViewerConfigUseCase {
    override fun validate(config: ViewerConfig) {
        config.validate()
    }

    override suspend fun getConfig(): ViewerConfig {
        //if settings are corrupted lets just create a new one
        return runCatching { settings.getViewerConfig() }.getOrNull().getOrNew()
    }

    override suspend fun saveConfig(config: ViewerConfig) {
        settings.saveViewerConfig(config.also { it.validate() })
    }

    override fun configFlow() =
        flowOf(flow { emit(settings.getViewerConfig()) }, settings.viewerConfigFlow())
            .flattenConcat()
            .mapNotNull { it.getOrNew() }

    companion object {
        private fun ViewerConfig?.getOrNew(): ViewerConfig =
            this?.let { runCatching { it.also { it.validate() } }.getOrNull() }
                ?: ViewerConfig()

        private fun ViewerConfig.validate() {
            try {
                if (brightness !in ViewerConfig.SYSTEM_BRIGHTNESS..1.0f) {
                    throw IllegalArgumentException()
                }
            } catch (t: Throwable) {
                Logger.error(t, "Invalid comic book viewer config $this")
                throw t
            }
        }
    }
}