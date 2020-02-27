package com.almadevelop.comixreader.work.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.almadevelop.comixreader.logic.comic.Library
import org.koin.core.KoinComponent
import org.koin.core.inject

class SyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {
    private val library by inject<Library>()

    override suspend fun doWork(): Result {
        library.sync()

        return Result.success()
    }
}