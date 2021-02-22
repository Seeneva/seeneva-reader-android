package com.almadevelop.comixreader.logic.extension

import com.almadevelop.comixreader.data.source.local.db.dao.ComicBookPageSource
import com.almadevelop.comixreader.logic.entity.ml.ObjectClass

/**
 * Helper to query page objects using [ObjectClass]
 * @see ComicBookPageSource.objectsDataById
 */
internal suspend fun ComicBookPageSource.objectsDataById(
    id: Long,
    classIds: Set<ObjectClass> = emptySet()
) = objectsDataById(id, classIds.mapTo(hashSetOf()) { it.id })