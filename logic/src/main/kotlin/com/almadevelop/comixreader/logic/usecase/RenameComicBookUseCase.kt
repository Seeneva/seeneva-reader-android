package com.almadevelop.comixreader.logic.usecase

import com.almadevelop.comixreader.common.coroutines.Dispatched
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookSource

interface RenameComicBookUseCase {
    suspend fun byComicBookId(id: Long, title: String)
}

internal class RenameComicBookUseCaseImpl(
    private val comicBookSource: ComicBookSource,
    override val dispatchers: Dispatchers
) : RenameComicBookUseCase, Dispatched {
    override suspend fun byComicBookId(id: Long, title: String) {
        comicBookSource.updateTitle(id, title)
    }
}