package com.almadevelop.comixreader.logic.usecase.tags

import com.almadevelop.comixreader.data.source.local.db.LocalTransactionRunner
import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookSource
import com.almadevelop.comixreader.data.source.local.db.dao.ComicTagSource
import com.almadevelop.comixreader.logic.entity.TagType
import com.almadevelop.comixreader.logic.extension.getOrCreateHardcodedTagId

interface ComicCompletedTagUseCase :
    ComicTagUseCase

internal class ComicCompletedTagUseCaseImpl(
    metadataSource: ComicBookSource,
    localTransactionRunner: LocalTransactionRunner,
    private val comicTagSource: ComicTagSource
) : ComicCompletedTagUseCase, BaseComicTagUseCase(metadataSource, localTransactionRunner) {
    override suspend fun getTagId(): Long =
        comicTagSource.getOrCreateHardcodedTagId(TagType.TYPE_COMPLETED)
}