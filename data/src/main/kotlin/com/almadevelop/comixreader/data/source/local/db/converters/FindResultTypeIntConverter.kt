package com.almadevelop.comixreader.data.source.local.db.converters

import androidx.room.TypeConverter
import com.almadevelop.comixreader.data.entity.FindResult

internal class FindResultTypeIntConverter {
    @TypeConverter
    fun intToType(value: Int): FindResult.Type =
        when (value) {
            FindResult.SQL_BY_PATH -> FindResult.Type.Path
            FindResult.SQL_BY_CONTENT -> FindResult.Type.Content
            else -> throw IllegalArgumentException("Unknown find result type: '$value'")
        }
}