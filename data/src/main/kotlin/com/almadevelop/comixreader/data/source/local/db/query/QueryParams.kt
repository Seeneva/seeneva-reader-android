package com.almadevelop.comixreader.data.source.local.db.query

private typealias TagId = Long

typealias TagsFilters = Map<TagId, TagFilterType>?

interface PagedQueryParams {
    /**
     * limit how many items
     */
    val limit: Int?
    /**
     * offset from the start
     */
    val offset: Int?
}

interface FilterQueryParams {
    val title: String?
    val tagsFilters: TagsFilters
}

/**
 * @param limit how many items
 * @param offset offset from the start
 * @param title comic book title should contain it
 * @param sort how to sort result list
 */
data class QueryParams(
    override val limit: Int? = null,
    override val offset: Int? = null,
    override val title: String? = null,
    override val tagsFilters: TagsFilters = null,
    val sort: QuerySort? = null
) : PagedQueryParams,
    FilterQueryParams {
    companion object {
        val EMPTY =
            QueryParams()
    }
}

data class CountQueryParams(
    override val title: String? = null,
    override val tagsFilters: TagsFilters = null
) : FilterQueryParams {
    companion object {
        val EMPTY =
            CountQueryParams()
    }
}

enum class QuerySort {
    OpenTimeDesc,
    OpenTimeAsc,
    NameAsc,
    NameDesc;
}

enum class TagFilterType { Include, Exclude }