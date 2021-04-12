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

package app.seeneva.reader.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import androidx.annotation.CallSuper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import app.seeneva.reader.AppNotification
import app.seeneva.reader.R

abstract class BaseForegroundService : LifecycleService() {
    protected abstract val rootNotificationId: Int

    @CallSuper
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        return when (val result = onStartCommandInner(intent, flags, startId)) {
            is CommandResult.Foreground -> {
                startForeground(rootNotificationId, result.notification)
                result.startState
            }
            is CommandResult.NonForeground -> result.startState
        }
    }

    /**
     * Remove notification and stop service
     */
    protected fun fullStop() {
        stopForeground(true)

        stopSelf()
    }

    protected open fun onStartCommandInner(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): CommandResult {
        return CommandResult.NonForeground()
    }

    protected inline fun newNotification(block: NotificationCompat.Builder.() -> Unit = {}): Notification =
        NotificationCompat.Builder(this, AppNotification.Channel.FOREGROUND_TASK)
            .setSmallIcon(R.drawable.ic_notification)
            .apply(block)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setChannelId(AppNotification.Channel.FOREGROUND_TASK)
            .build()

    protected sealed class CommandResult {
        data class Foreground(
            val notification: Notification,
            val startState: Int = Service.START_NOT_STICKY
        ) : CommandResult()

        data class NonForeground(val startState: Int = Service.START_NOT_STICKY) : CommandResult()
    }
}

