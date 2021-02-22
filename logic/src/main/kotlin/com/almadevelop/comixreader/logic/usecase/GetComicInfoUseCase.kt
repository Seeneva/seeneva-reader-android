package com.almadevelop.comixreader.logic.usecase

import android.content.Context
import com.almadevelop.comixreader.common.coroutines.Dispatched
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.common.coroutines.io
import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookSource
import com.almadevelop.comixreader.logic.entity.ComicInfo
import com.almadevelop.comixreader.logic.mapper.ComicMetadataIntoComicInfo
import org.tinylog.kotlin.Logger

interface GetComicInfoUseCase {
    suspend fun byId(id: Long): ComicInfo?
}

internal class GetComicInfoUseCaseImpl(
    context: Context,
    private val comicBookSource: ComicBookSource,
    private val mapper: ComicMetadataIntoComicInfo,
    override val dispatchers: Dispatchers
) : GetComicInfoUseCase, Dispatched {
    private val context = context.applicationContext

    override suspend fun byId(id: Long) =
        io {
            try {
                mapper(comicBookSource.getFullById(id), context)
            } catch (t: Throwable) {
                Logger.error(t, "Can't get comic book info by id: $t")
                throw t
            }
        }
}