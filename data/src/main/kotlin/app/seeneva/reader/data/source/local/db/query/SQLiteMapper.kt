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

package app.seeneva.reader.data.source.local.db.query

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import app.seeneva.reader.data.entity.ComicBook
import app.seeneva.reader.data.source.local.db.entity.TaggedComicBook

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

    appendLine("$columns FROM ${ComicBook.TABLE_NAME}")
}

private fun StringBuilder.appendJoinClause(query: FilterQueryParams) {
    if (query.tagsFilters.isNullOrEmpty()) {
        return
    }

    //need to jon comic_book -> tag table to filter by tabs
    appendLine("LEFT JOIN ${TaggedComicBook.TABLE_NAME} ON ${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_ID} = ${TaggedComicBook.TABLE_NAME}.${TaggedComicBook.COLUMN_BOOK_ID}")
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
        appendLine("WHERE $whereClause")
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

    appendLine("ORDER BY $sortCause")
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
        appendLine("LIMIT $limitOffsetClause")
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

    filters.forEach { (filterType, filterIds) ->
        if (!filterIds.isNullOrEmpty()) {
            if (isNotEmpty()) {
                append(" AND ")
            }

            args.addAll(filterIds)

            val inOperator: String
            val havingClause: String

            when (filterType) {
                TagFilterType.Include -> {
                    inOperator = "IN"
                    havingClause = "HAVING COUNT(${TaggedComicBook.COLUMN_TAG_ID}) = ?"
                    args += filterIds.size
                }
                TagFilterType.Exclude -> {
                    inOperator = "NOT IN"
                    havingClause = ""
                }
            }

            append(
                """${ComicBook.TABLE_NAME}.${ComicBook.COLUMN_ID}
                |$inOperator (SELECT ${TaggedComicBook.COLUMN_BOOK_ID} FROM ${TaggedComicBook.TABLE_NAME}
                |WHERE ${TaggedComicBook.COLUMN_TAG_ID} IN (${filterIds.placeHolders()})
                |GROUP BY ${TaggedComicBook.COLUMN_BOOK_ID}
                |$havingClause)
            """.trimMargin()
            )
        }
    }
}

/**
 * Represent as SQLite args placeholder string
 */
private fun Iterable<*>.placeHolders(): String = joinToString { "?" }