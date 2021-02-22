package com.almadevelop.comixreader.logic

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.almadevelop.comixreader.common.coroutines.Dispatched
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.common.coroutines.io
import com.almadevelop.comixreader.logic.entity.configuration.ViewerConfig
import com.almadevelop.comixreader.logic.entity.configuration.deserializeViewerConfiguration
import com.almadevelop.comixreader.logic.entity.configuration.serialize
import com.almadevelop.comixreader.logic.entity.query.QueryParams
import com.almadevelop.comixreader.logic.entity.query.deserializeQueryParams
import com.almadevelop.comixreader.logic.entity.query.filter.FilterProvider
import com.almadevelop.comixreader.logic.entity.query.serialize
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

//TODO make all methods suspend

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
     * Restore last saved titleQuery params
     * @return last saved titleQuery params or default
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
    private val filterProvider: FilterProvider,
    override val dispatchers: Dispatchers
) : ComicsSettings, Dispatched {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val editorMutex = Mutex()

    override suspend fun saveComicListQueryParams(params: QueryParams) {
        io {
            val serializedParams = params.serialize()

            editorMutex.withLock {
                prefs.edit(true) { putString(KEY_COMIC_LIST_PARAMS, serializedParams) }
            }
        }
    }

    override fun getComicListQueryParams(): QueryParams =
        deserializeQueryParams(
            prefs.getString(
                KEY_COMIC_LIST_PARAMS,
                null
            )
        ) { filterProvider.groups() }

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
            val serializedConfig = viewerConfig.serialize()

            editorMutex.withLock {
                prefs.edit(true) { putString(KEY_VIEWER_CONFIG, serializedConfig) }
            }
        }
    }

    override suspend fun getViewerConfig() =
        io {
            val serializedConfig = prefs.getString(KEY_VIEWER_CONFIG, null)

            ensureActive()

            deserializeViewerConfiguration(serializedConfig)
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
                offer(key)
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