package com.almadevelop.comixreader.logic.usecase.tags

import com.almadevelop.comixreader.data.source.local.db.LocalTransactionRunner
import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookSource
import com.almadevelop.comixreader.data.source.local.db.dao.ComicTagSource
import com.almadevelop.comixreader.logic.entity.TagType
import com.almadevelop.comixreader.logic.extension.getOrCreateHardcodedTagId

interface ComicRemovedTagUseCase :
    ComicTagUseCase

internal class ComicRemovedStateUseCaseImpl(
    comicBookSource: ComicBookSource,
    localTransactionRunner: LocalTransactionRunner,
    private val comicTagSource: ComicTagSource
) : ComicRemovedTagUseCase, BaseComicTagUseCase(comicBookSource, localTransactionRunner) {
    override suspend fun getTagId(): Long =
        comicTagSource.getOrCreateHardcodedTagId(TagType.TYPE_REMOVED)
}