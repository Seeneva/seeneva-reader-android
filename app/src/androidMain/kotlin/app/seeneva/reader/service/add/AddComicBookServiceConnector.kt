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

package app.seeneva.reader.service.add

import android.content.Context
import android.net.Uri
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.logic.comic.AddComicBookMode
import app.seeneva.reader.logic.entity.ComicAddResult
import app.seeneva.reader.service.BaseServiceConnector
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.transformLatest

interface AddComicBookServiceConnector {
    /**
     * @see AddComicBookServiceBinder.add
     */
    suspend fun add(
        path: Uri,
        addComicBookMode: AddComicBookMode,
        openFlags: Int
    ): ComicAddResult {
        return add(listOf(path), addComicBookMode, openFlags).single()
    }

    /**
     * @see AddComicBookServiceBinder.add
     */
    fun add(
        paths: List<Uri>,
        addComicBookMode: AddComicBookMode,
        openFlags: Int
    ): Flow<ComicAddResult>

    /**
     * @see AddComicBookServiceBinder.cancel
     */
    suspend fun cancel(comicBookPath: Uri): Boolean
}

class AddComicBookServiceConnectorImpl(
    context: Context,
    parent: Job?,
    dispatchers: Dispatchers
) : BaseServiceConnector<AddComicBookService, AddComicBookServiceBinder>(
    context,
    parent,
    dispatchers,
    AddComicBookService::class.java
), AddComicBookServiceConnector {
    override fun add(
        paths: List<Uri>,
        addComicBookMode: AddComicBookMode,
        openFlags: Int
    ) = binderFlow.transformLatest {
        //We need to prevent Service binder from disconnect
        // so it is really critical to transform this FLow into Service Flow
        if (it is BinderState.Connected<AddComicBookServiceBinder>) {
            emitAll(it.binder.add(paths, addComicBookMode, openFlags))
            //We do not need this SharedFlow anymore. We need to call cancel here to close subscription.
            //If Service doesn't have any work to do and its binder was disconnected it will be destroyed
            currentCoroutineContext().cancel()
        }
    }

    override suspend fun cancel(comicBookPath: Uri) =
        onBind { it.cancel(comicBookPath) }
}