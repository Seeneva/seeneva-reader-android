/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
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

package app.seeneva.reader.data.source.local.db

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.seeneva.reader.data.entity.*
import app.seeneva.reader.data.source.local.db.converters.InstantLongConverter
import app.seeneva.reader.data.source.local.db.converters.UriStringConverter
import app.seeneva.reader.data.source.local.db.dao.*
import app.seeneva.reader.data.source.local.db.entity.ComicPageObjectText
import app.seeneva.reader.data.source.local.db.entity.TaggedComicBook
import java.util.concurrent.Executor

private const val DB_VERSION = 1
private const val DB_NAME = "reader_data.db"

@Database(
    version = DB_VERSION,
    entities = [
        ComicBook::class,
        ComicBookPage::class,
        ComicRackMetadata::class,
        ComicRackPageMetadata::class,
        ComicTag::class,
        TaggedComicBook::class,
        ComicPageObject::class,
        ComicPageObjectText::class,
    ]
)
@TypeConverters(
    value = [UriStringConverter::class,
        InstantLongConverter::class]
)
internal abstract class ComicDatabase : RoomDatabase() {
    abstract fun comicBookSource(): ComicBookSource

    abstract fun comicBookPageSource(): ComicBookPageSource

    abstract fun comicBookPageObjectSource(): ComicPageObjectSource

    abstract fun comicRackMetadataSource(): ComicRackMetadataSource

    abstract fun comicRackPageMetadataSource(): ComicRackPageMetadataSource

    abstract fun comicTagSource(): ComicTagSource

    abstract fun taggedComicBookSource(): TaggedComicBookSource

    companion object {
        @Volatile
        private var dbInstance: ComicDatabase? = null

        /**
         * Get database instance
         * @param context
         * @param queryExecutor
         * @param inMemory create inMemory database
         */
        fun instance(
            context: Context,
            queryExecutor: Executor,
            inMemory: Boolean = false
        ): ComicDatabase {
            if (dbInstance == null) {
                synchronized(this) {
                    if (dbInstance == null) {
                        val klass = ComicDatabase::class.java

                        dbInstance = if (inMemory) {
                            Room.inMemoryDatabaseBuilder(context, klass)
                        } else {
                            Room.databaseBuilder(context, klass, DB_NAME)
                        }.setQueryExecutor(queryExecutor).build()
                    }
                }
            }

            return requireNotNull(dbInstance)
        }

        @VisibleForTesting
        internal fun closeInstance() {
            synchronized(this) {
                dbInstance?.close()
                dbInstance = null
            }
        }
    }
}