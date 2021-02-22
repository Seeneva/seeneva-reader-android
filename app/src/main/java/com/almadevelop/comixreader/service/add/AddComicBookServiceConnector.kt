package com.almadevelop.comixreader.service.add

import android.content.Context
import android.net.Uri
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.comic.AddComicBookMode
import com.almadevelop.comixreader.logic.entity.ComicAddResult
import com.almadevelop.comixreader.service.BaseServiceConnector
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