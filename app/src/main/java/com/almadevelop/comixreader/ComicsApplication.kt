package com.almadevelop.comixreader

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.almadevelop.comixreader.AppNotification.createComicNotificationChannels
import com.almadevelop.comixreader.di.setup
import com.almadevelop.comixreader.work.SyncManager
import com.jakewharton.threetenabp.AndroidThreeTen
import org.koin.android.ext.android.get
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

class ComicsApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        AndroidThreeTen.init(this)

        createComicNotificationChannels()

        startKoin { setup(this@ComicsApplication) }

        prepare()
    }

    /**
     * Additional preparations
     */
    private fun prepare() {
        get<SyncManager>().syncPeriodically(12, TimeUnit.HOURS)
    }
}