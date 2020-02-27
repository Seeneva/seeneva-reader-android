package com.almadevelop.comixreader.logic.entity.query

import com.almadevelop.comixreader.logic.entity.query.filter.FilterGroup
import org.json.JSONArray
import org.json.JSONObject
import org.tinylog.kotlin.Logger

private object Keys {
    const val SORT = "sort"
    const val FILTERS = "filters"

    const val FILTER_GROUP_ID = "group_id"
    const val FILTER_ID = "id"
}

/**
 * Serialize comic books titleQuery params into string
 * @return titleQuery params as string
 */
internal fun QueryParams.serialize(): String = JSONObject().also { jsonObject ->
    jsonObject.put(Keys.SORT, sort.key)

    jsonObject.put(Keys.FILTERS, JSONArray().also { jsonArray ->
        filters.forEach { (filterGroupKey, filter) ->
            jsonArray.put(JSONObject().put(Keys.FILTER_ID, filter.id).put(Keys.FILTER_GROUP_ID, filterGroupKey.name))
        }
    })
}.toString()

/**
 * Deserialize comic book titleQuery params from string
 * @param query source
 * @return comic query params or default value if [query] is null
 */
internal fun deserializeQueryParams(query: String?, filtersProvider: () -> List<FilterGroup>): QueryParams {
    return QueryParams.build {
        if (!query.isNullOrEmpty()) {
            try {
                val rootJson = JSONObject(query)

                sort = rootJson.getString(Keys.SORT).let { QuerySort.fromKey(it) }

                addFilters(rootJson.getJSONArray(Keys.FILTERS), filtersProvider)
            } catch (t: Throwable) {
                Logger.error(t)
            }
        }
    }
}

private fun QueryParams.Builder.addFilters(
    filtersJsonArray: JSONArray,
    filtersProvider: () -> List<FilterGroup>
) {
    if (filtersJsonArray.length() == 0) {
        return
    }

    val filters =
        filtersProvider().associate { filterGroup -> filterGroup.id to filterGroup.filters.associateBy { it.id } }

    for (index in 0 until filtersJsonArray.length()) {
        val filterJson = filtersJsonArray.getJSONObject(index)

        val filterGroupId = filterJson.getString(Keys.FILTER_GROUP_ID)
            .let { runCatching { FilterGroup.ID.valueOf(it) }.getOrNull() }

        if (filterGroupId != null) {
            val filterId = filterJson.getString(Keys.FILTER_ID)

            filters[filterGroupId]?.get(filterId)?.also { addFilter(filterGroupId, it) }
        }
    }
}

