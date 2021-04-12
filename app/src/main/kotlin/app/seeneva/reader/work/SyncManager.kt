/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
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

package app.seeneva.reader.work

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.seeneva.reader.work.worker.SyncWorker
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