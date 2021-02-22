package com.almadevelop.comixreader.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import androidx.annotation.CallSuper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.almadevelop.comixreader.AppNotification
import com.almadevelop.comixreader.R

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

