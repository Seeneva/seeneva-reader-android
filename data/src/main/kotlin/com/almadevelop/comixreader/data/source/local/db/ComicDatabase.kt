package com.almadevelop.comixreader.data.source.local.db

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.almadevelop.comixreader.data.entity.*
import com.almadevelop.comixreader.data.source.local.db.converters.FindResultTypeIntConverter
import com.almadevelop.comixreader.data.source.local.db.converters.InstantLongConverter
import com.almadevelop.comixreader.data.source.local.db.converters.UriStringConverter
import com.almadevelop.comixreader.data.source.local.db.dao.*
import com.almadevelop.comixreader.data.source.local.db.entity.ComicPageObjectText
import com.almadevelop.comixreader.data.source.local.db.entity.TaggedComicBook
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
        InstantLongConverter::class,
        FindResultTypeIntConverter::class]
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