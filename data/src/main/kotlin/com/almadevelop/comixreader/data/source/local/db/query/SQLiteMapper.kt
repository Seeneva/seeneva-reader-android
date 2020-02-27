package com.almadevelop.comixreader.data.source.local.db.query

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.almadevelop.comixreader.data.entity.ComicBook
import com.almadevelop.comixreader.data.entity.ComicTag
import com.almadevelop.comixreader.data.source.local.db.entity.TaggedComicBook

private const val ASC = "ASC"
private const val DESC = "DESC"

private const val ORDER_NAME_ASC = "${ComicBook.COLUMN_DISPLAY_NAME} $ASC"
private const val ORDER_NAME_DESC = "${ComicBook.COLUMN_DISPLAY_NAME} $DESC"
private const val ORDER_ACTION_TIME_ASC = "${ComicBook.COLUMN_ACTION_TIME} $ASC"
private const val ORDER_ACTION_TIME_DESC = "${ComicBook.COLUMN_ACTION_TIME} $DESC"

/**
 * Convert [QueryParams] into Room SQLite querySimpleWithTags
 * @param comicBookSelectColumns comma separated columns for [ComicBook] entity
 * @return Room SQLite querySimpleWithTags
 */
internal fun QueryParams.intoSQLiteQuery(comicBookSelectColumns: String = "${ComicBook.TABLE_NAME}.*"): SupportSQLiteQuery {
    val args = arrayListOf<Any>()

    return SimpleSQLiteQuery(buildString {
        appendSelectClause(
            !tagsFilters.isNullOrEmpty(),
            comicBookSelectColumns
        )

        appendJoinClause(this@intoSQLiteQuery)

        appendWhereClause(this@intoSQLiteQuery, args)

        appendOrderByCause(sort)

        appendLimitClause(this@intoSQLiteQuery, args)
    }, args.toArray())
}

internal fun CountQueryParams.intoCountSQLiteQuery(): SupportSQLiteQuery {
    val args = arrayListOf<Any>()

    return SimpleSQLiteQuery(buildString {
        appendSelectClause(false, "COUNT(DISTINCT ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_ID})")

        appendJoinClause(this@intoCountSQLiteQuery)

        appendWhereClause(this@intoCountSQLiteQuery, args)
    }, args.toArray())
}

/**
 * SELECT clause
 */
private fun StringBuilder.appendSelectClause(distinct: Boolean, columns: String) {
    append("SELECT ")

    if (distinct) {
        append("DISTINCT ")
    }

    appendln("$columns FROM ${ComicBook.TABLE_NAME}")
}

private fun StringBuilder.appendJoinClause(query: FilterQueryParams) {
    if (query.tagsFilters.isNullOrEmpty()) {
        return
    }

    //need to jon comic_book -> tag table to filter by tabs
    appendln("LEFT JOIN ${TaggedComicBook.TABLE_NAME} ON ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_ID} = ${TaggedComicBook.TABLE_NAME}.${TaggedComicBook.COLUMN_BOOK_ID}")
}

/**
 * WHERE clause
 * @param args SDQLite args list
 */
private fun StringBuilder.appendWhereClause(query: FilterQueryParams, args: MutableList<Any>) {
    val whereClause = buildString {
        query.title.also { title ->
            if (!title.isNullOrEmpty()) {
                // || - SQL concatenation
                append("${ComicBook.COLUMN_DISPLAY_NAME} LIKE '%' || ? || '%'")

                args += title
            }
        }

        query.tagsFilters.also { tagsFilters ->
            if (!tagsFilters.isNullOrEmpty()) {
                val idsFilters = hashMapOf<TagFilterType, MutableSet<Long>>()

                tagsFilters.forEach { (id, type) ->
                    idsFilters.getOrPut(type) { hashSetOf() }.add(id)
                }

                appendTagFilters(idsFilters, args)
            }
        }
    }

    if (whereClause.isNotEmpty()) {
        appendln("WHERE $whereClause")
    }
}

/**
 * ORDER BY clause
 */
private fun StringBuilder.appendOrderByCause(sort: QuerySort?) {
    if (sort == null) {
        return
    }

    val sortCause = when (sort) {
        QuerySort.NameAsc -> ORDER_NAME_ASC
        QuerySort.NameDesc -> ORDER_NAME_DESC
        QuerySort.OpenTimeAsc -> ORDER_ACTION_TIME_ASC
        QuerySort.OpenTimeDesc -> ORDER_ACTION_TIME_DESC
    }

    appendln("ORDER BY $sortCause")
}

/**
 * LIMIT clause
 * @param args SQLite args list
 */
private fun StringBuilder.appendLimitClause(query: PagedQueryParams, args: MutableList<Any>) {
    val sqlLimit = query.limit ?: -1
    val sqlOffset = query.offset ?: -1

    val limitOffsetClause = when {
        sqlOffset >= 0 -> {
            args += sqlLimit
            args += sqlOffset

            "? OFFSET ?"
        }
        sqlLimit >= 0 -> {
            args += sqlLimit

            "?"
        }
        else -> null
    }

    if (!limitOffsetClause.isNullOrEmpty()) {
        appendln("LIMIT $limitOffsetClause")
    }
}

/**
 * Add tag filters into WHERE clause
 * @param filters filters to apply
 * @param args SQLite args list
 */
private fun StringBuilder.appendTagFilters(
    filters: Map<TagFilterType, Set<Long>>,
    args: MutableList<Any>
) {
    if (filters.isEmpty()) {
        return
    }

    filters[TagFilterType.Include].also { include ->
        if (!include.isNullOrEmpty()) {
            if (isNotEmpty()) {
                append(" AND ")
            }

            append("${TaggedComicBook.TABLE_NAME}.${TaggedComicBook.COLUMN_TAG_ID} IN (${include.placeHolders()})")

            args.addAll(include)
        }
    }

    filters[TagFilterType.Exclude].also { exclude ->
        if (!exclude.isNullOrEmpty()) {
            if (isNotEmpty()) {
                append(" AND ")
            }

            append(
                """${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_ID}
                    |NOT IN (SELECT ${TaggedComicBook.COLUMN_BOOK_ID} FROM ${TaggedComicBook.TABLE_NAME}
                    |WHERE ${TaggedComicBook.COLUMN_TAG_ID} IN (${exclude.placeHolders()}))""".trimMargin()
            )

            args.addAll(exclude)
        }
    }
}

/**
 * Represent as SQLite args placeholder string
 */
private fun Iterable<*>.placeHolders(): String = joinToString { "?" }