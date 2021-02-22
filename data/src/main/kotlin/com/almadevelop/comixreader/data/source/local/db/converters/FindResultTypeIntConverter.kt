package com.almadevelop.comixreader.data.source.local.db.converters

import androidx.room.TypeConverter
import com.almadevelop.comixreader.data.entity.FindResult

internal class FindResultTypeIntConverter {
    @TypeConverter
    fun intToType(value: Int): FindResult.Type =
        FindResult.Type.values().firstOrNull { it.id == value }
            ?: throw IllegalArgumentException("Unknown find result type: '$value'")
}