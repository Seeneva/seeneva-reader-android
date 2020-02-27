package com.almadevelop.comixreader.data.source.local.db.converters

import androidx.room.TypeConverter
import org.threeten.bp.Instant

internal class InstantLongConverter {
    /**
     * Format into UTC seconds
     */
    @TypeConverter
    fun instantToString(input: Instant): Long =
        input.toEpochMilli()

    /**
     * From UTC seconds
     */
    @TypeConverter
    fun stringToInstant(input: Long): Instant {
        return Instant.ofEpochMilli(input)
    }
}