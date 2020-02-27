package com.almadevelop.comixreader.data.source.local.db.converters

import android.net.Uri
import androidx.core.net.toUri
import androidx.room.TypeConverter

internal class UriStringConverter {
    @TypeConverter
    fun uriToString(input: Uri?): String? = input?.toString()

    @TypeConverter
    fun stringToUri(input: String?): Uri? = input?.toUri()
}