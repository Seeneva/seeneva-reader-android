package com.almadevelop.comixreader.service.add

import android.content.Context
import android.net.Uri
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.comic.AddComicBookMode
import com.almadevelop.comixreader.logic.entity.ComicAddResult
import com.almadevelop.comixreader.service.BaseServiceConnector
import com.almadevelop.comixreader.service.ServiceConnector
import kotlinx.coroutines.flow.Flow
import java.util.*

interface AddComicBookServiceConnector : ServiceConnector {
    /**
     * @see AddComicBookServiceBinder.subscribe
     */
    suspend fun subscribe(): Flow<ComicAddResult>

    /**
     * @see AddComicBookServiceBinder.add
     */
    suspend fun add(
        path: Uri,
        addComicBookMode: AddComicBookMode,
        openFlags: Int
    ): ComicAddResult {
        return add(Collections.singletonList(path), addComicBookMode, openFlags).first()
    }

    /**
     * @see AddComicBookServiceBinder.add
     */
    suspend fun add(
        paths: List<Uri>,
        addComicBookMode: AddComicBookMode,
        openFlags: Int
    ): List<ComicAddResult>

    /**
     * @see AddComicBookServiceBinder.cancel
     */
    suspend fun cancel(comicBookPath: Uri): Boolean
}

class AddComicBookServiceConnectorImpl(context: Context, dispatchers: Dispatchers) :
    BaseServiceConnector<AddComicBookServiceBinder>(context, dispatchers),
    AddComicBookServiceConnector {
    override suspend fun subscribe(): Flow<ComicAddResult> {
        return bind().subscribe()
    }

    override suspend fun add(
        paths: List<Uri>,
        addComicBookMode: AddComicBookMode,
        openFlags: Int
    ): List<ComicAddResult> {
        return bind().add(paths, addComicBookMode, openFlags)
    }

    override suspend fun cancel(comicBookPath: Uri): Boolean {
        return bind().cancel(comicBookPath)
    }

    private suspend fun bind() =
        bind(AddComicBookService::class.java)
}