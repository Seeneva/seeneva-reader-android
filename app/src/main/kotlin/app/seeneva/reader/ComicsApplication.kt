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

package app.seeneva.reader

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDex
import app.seeneva.reader.AppNotification.createComicNotificationChannels
import app.seeneva.reader.di.setup
import app.seeneva.reader.work.SyncManager
import org.koin.android.ext.android.get
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

class ComicsApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        createComicNotificationChannels()

        startKoin { setup(this@ComicsApplication) }

        prepare()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    /**
     * Additional preparations
     */
    private fun prepare() {
        get<SyncManager>().syncPeriodically(12, TimeUnit.HOURS)
    }
}