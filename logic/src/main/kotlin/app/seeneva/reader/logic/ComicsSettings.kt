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

package app.seeneva.reader.logic

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.seeneva.reader.common.coroutines.Dispatched
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.common.coroutines.io
import app.seeneva.reader.logic.entity.configuration.ViewerConfig
import app.seeneva.reader.logic.entity.query.QueryParams
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.tinylog.kotlin.Logger

//TODO make all methods suspend or use https://developer.android.com/jetpack/androidx/releases/datastore

/**
 * Different settings of the application
 */
interface ComicsSettings {
    /**
     * Save comic list titleQuery params
     * @param params params to save
     */
    suspend fun saveComicListQueryParams(params: QueryParams)

    /**
     * Restore last saved comic book query params
     * @return last saved comic book query params or default
     */
    fun getComicListQueryParams(): QueryParams

    /**
     * Save last chosen comic books list view type
     * @param listType type to save
     */
    fun saveComicListType(listType: ComicListViewType)

    /**
     * Get last chosen comic books list view type
     * @return comic book list view type
     */
    fun getComicListType(): ComicListViewType

    /**
     * Save viewer configuration
     */
    suspend fun saveViewerConfig(viewerConfig: ViewerConfig)

    /**
     * Get viewer configuration
     */
    suspend fun getViewerConfig(): ViewerConfig?

    /**
     * Emit flow of viewer configuration
     */
    fun viewerConfigFlow(): Flow<ViewerConfig?>

    /**
     * @return true if help should be showed to a user
     */
    suspend fun shouldShowViewerHelp(): Boolean

    suspend fun setShouldShowViewerHelp(v: Boolean)

    /**
     * @see shouldShowViewerHelp
     */
    fun shouldShowViewerHelpFlow(): Flow<Boolean>
}

internal class PrefsComicsSettings(
    context: Context,
    private val json: Json,
    override val dispatchers: Dispatchers
) : ComicsSettings, Dispatched {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val editorMutex = Mutex()

    override suspend fun saveComicListQueryParams(params: QueryParams) {
        io {
            val serializedParams = json.encodeToString(params)

            Logger.debug { "Save list query params $params as JSON $serializedParams" }

            ensureActive()

            editorMutex.withLock {
                prefs.edit(true) { putString(KEY_COMIC_LIST_PARAMS, serializedParams) }
            }
        }
    }

    override fun getComicListQueryParams(): QueryParams =
        prefs.getString(KEY_COMIC_LIST_PARAMS, null)
            ?.let {
                // ignore decoding errors and build default QueryParams in case of any
                runCatching {
                    json.decodeFromString<QueryParams>(it)
                }.onFailure {
                    Logger.error(it, "Can't decode list query params")
                }.getOrNull()
            }
            ?: QueryParams.build()

    override fun saveComicListType(listType: ComicListViewType) {
        prefs.edit { putString(KEY_COMIC_LIST_VIEW_TYPE, listType.name) }
    }

    override fun getComicListType(): ComicListViewType {
        return prefs.getString(KEY_COMIC_LIST_VIEW_TYPE, null)
            ?.let { runCatching { ComicListViewType.valueOf(it) }.getOrNull() }
            ?: ComicListViewType.default
    }

    override suspend fun saveViewerConfig(viewerConfig: ViewerConfig) {
        io {
            val serializedConfig = json.encodeToString(viewerConfig)

            Logger.debug { "Save viewer config $viewerConfig  as JSON $serializedConfig" }

            ensureActive()

            editorMutex.withLock {
                prefs.edit(true) { putString(KEY_VIEWER_CONFIG, serializedConfig) }
            }
        }
    }

    override suspend fun getViewerConfig() =
        io {
            val serializedConfig = prefs.getString(KEY_VIEWER_CONFIG, null)

            ensureActive()

            serializedConfig?.let { json.decodeFromString<ViewerConfig>(it) }
        }

    override fun viewerConfigFlow() =
        updateFlow().filter { it == KEY_VIEWER_CONFIG }.map { getViewerConfig() }

    override suspend fun shouldShowViewerHelp() =
        io { prefs.getBoolean(KEY_SHOW_VIEWER_HELP, true) }


    override suspend fun setShouldShowViewerHelp(v: Boolean) =
        io { editorMutex.withLock { prefs.edit(true) { putBoolean(KEY_SHOW_VIEWER_HELP, v) } } }

    override fun shouldShowViewerHelpFlow() =
        updateFlow().filter { it == KEY_SHOW_VIEWER_HELP }
            .map { shouldShowViewerHelp() }
            .onStart { emit(shouldShowViewerHelp()) }

    private fun updateFlow() =
        callbackFlow<String> {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                trySend(key)
            }

            prefs.registerOnSharedPreferenceChangeListener(listener)

            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }.flowOn(dispatchers.main).conflate()

    companion object {
        private const val PREF_NAME = "settings"

        private const val KEY_COMIC_LIST_PARAMS = "comic_list_params"
        private const val KEY_COMIC_LIST_VIEW_TYPE = "comic_list_vew_type"
        private const val KEY_VIEWER_CONFIG = "viewer_config"
        private const val KEY_SHOW_VIEWER_HELP = "show_viewer_help"
    }
}