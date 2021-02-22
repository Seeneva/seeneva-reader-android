package com.almadevelop.comixreader

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDex
import com.almadevelop.comixreader.AppNotification.createComicNotificationChannels
import com.almadevelop.comixreader.di.setup
import com.almadevelop.comixreader.work.SyncManager
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