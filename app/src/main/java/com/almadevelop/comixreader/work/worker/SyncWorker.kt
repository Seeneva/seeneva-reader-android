package com.almadevelop.comixreader.work.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.almadevelop.comixreader.logic.comic.Library

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    library: Lazy<Library>
) : CoroutineWorker(appContext, params) {
    private val library by library

    override suspend fun doWork(): Result {
        library.sync()

        return Result.success()
    }
}