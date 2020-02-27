package com.almadevelop.comixreader.logic

import android.content.Context
import androidx.core.content.edit
import com.almadevelop.comixreader.logic.entity.query.QueryParams
import com.almadevelop.comixreader.logic.entity.query.deserializeQueryParams
import com.almadevelop.comixreader.logic.entity.query.filter.FilterProvider
import com.almadevelop.comixreader.logic.entity.query.serialize

/**
 * Different settings of the application
 */
interface ComicsSettings {
    /**
     * Save comic list titleQuery params
     * @param params params to save
     */
    fun saveComicListQueryParams(params: QueryParams)

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
}

class PrefsComicsSettings(
    context: Context,
    private val filterProvider: FilterProvider
) : ComicsSettings {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    override fun saveComicListQueryParams(params: QueryParams) {
        prefs.edit { putString(KEY_COMIC_LIST_PARAMS, params.serialize()) }
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

    companion object {
        private const val PREF_NAME = "settings"

        private const val KEY_COMIC_LIST_PARAMS = "comic_list_params"
        private const val KEY_COMIC_LIST_VIEW_TYPE = "comic_list_vew_type"
    }
}