package com.almadevelop.comixreader.work

import androidx.work.*
import com.almadevelop.comixreader.work.worker.SyncWorker
import java.util.concurrent.TimeUnit

interface SyncManager {
    /**
     * Periodically sync comic book library
     * @param interval sync period
     * @param timeUnit sync period unit
     */
    fun syncPeriodically(interval: Long, timeUnit: TimeUnit)
}

class SyncWorkManager(private val workManager: WorkManager) : SyncManager {
    override fun syncPeriodically(interval: Long, timeUnit: TimeUnit) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(interval, timeUnit)
            .build()

        workManager.enqueueUniquePeriodicWork(
            NAME_LIBRARY_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private companion object {
        const val NAME_LIBRARY_SYNC = "LIBRARY_SYNC"
    }
}