package com.almadevelop.comixreader.logic.entity.query.filter

/**
 * Represents available filter group
 *
 * @param id filter group id
 * @param title filter group title
 * @param filters child filters
 */
data class FilterGroup(
    val id: ID,
    val title: String,
    val filters: List<Filter>
) : Iterable<Filter> {
    operator fun get(index: Int) = filters[index]

    override fun iterator(): Iterator<Filter> {
        return filters.listIterator()
    }

    enum class ID { COMPLETION_STATUS, FILE_STATUS }
}