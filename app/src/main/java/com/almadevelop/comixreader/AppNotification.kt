package com.almadevelop.comixreader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object AppNotification {
    object Channel {
        /**
         * Channel for foreground tasks notifications
         */
        val FOREGROUND_TASK by lazy { "${javaClass.name}.CHANNEL_FOREGROUND_TASK" }
    }

    object Id {
        const val OPEN_COMIC_BOOK_METADATA = 100
    }

    object Group {
        const val OPEN_COMIC_BOOK_METADATA = "open_comic_book_metadata"
    }

    /**
     * Create required notification channels
     */
    fun Context.createComicNotificationChannels() {
        //Channels available only from Android 8
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = requireNotNull(getSystemService<NotificationManager>())

            if (notificationManager.getNotificationChannel(Channel.FOREGROUND_TASK) == null) {
                val channel = NotificationChannel(
                    Channel.FOREGROUND_TASK,
                    getString(R.string.notification_channel_foreground_task),
                    NotificationManager.IMPORTANCE_LOW
                )

                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}