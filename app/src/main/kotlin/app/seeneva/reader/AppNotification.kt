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