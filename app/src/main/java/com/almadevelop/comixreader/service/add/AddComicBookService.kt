package com.almadevelop.comixreader.service.add

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.almadevelop.comixreader.AppNotification
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.extension.humanDescription
import com.almadevelop.comixreader.extension.success
import com.almadevelop.comixreader.logic.comic.AddComicBookMode
import com.almadevelop.comixreader.logic.entity.ComicAddResult
import com.almadevelop.comixreader.logic.entity.FileData
import com.almadevelop.comixreader.service.BaseForegroundService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.koin.androidx.scope.currentScope
import org.koin.core.parameter.parametersOf
import java.util.*

class AddComicBookService : BaseForegroundService(), AddComicBookView {
    override val presenter by currentScope.inject<AddComicBookPresenter> { parametersOf(this as AddComicBookView) }

    override val rootNotificationId: Int
        get() = AppNotification.Id.OPEN_COMIC_BOOK_METADATA

    private val notificationManager by lazy { requireNotNull(getSystemService<NotificationManager>()) }

    override fun onStartCommandInner(intent: Intent?, flags: Int, startId: Int): CommandResult {
        return when (intent?.action) {
            ACTION_OPEN -> {
                val path = requireNotNull(intent.data) { "Comic book path cannot be null" }

                val accepted = presenter.add(startId, path, intent.extractOpenMode())

                //will resend every intent which wasn't stopped by startId
                CommandResult.Foreground(
                    rootForegroundNotification(), if (accepted) {
                        Service.START_REDELIVER_INTENT
                    } else {
                        Service.START_NOT_STICKY
                    }
                )
            }
            ACTION_CANCEL -> {
                presenter.cancelAdding(intent.data)

                CommandResult.NonForeground()
            }
            else -> super.onStartCommandInner(intent, flags, startId)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return BinderImpl(this, presenter)
    }

    override fun onAddingStarted(fileData: FileData) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //just add child notifications to the group
            notificationManager.notify(
                fileData.asNotificationId,
                newNotification {
                    setContentTitle(getString(R.string.notification_open_metadata_title))
                        .setOngoing(true) //prevent swipe to cancel gesture
                        .setContentText(fileData.name)
                        .setProgress(0, 0, true)
                        .setGroup(AppNotification.Group.OPEN_COMIC_BOOK_METADATA)
                        .addCancelAction(this@AddComicBookService, fileData.path)
                })
        } else {
            //we need to renotify root notification
            notifyRootNotification()
        }
    }

    override fun onAddingResult(fileData: FileData, result: ComicAddResult) {
        notificationManager.notify(
            fileData.asNotificationId,
            newNotification {
                setContentTitle(
                    getString(
                        if (result.success) {
                            R.string.notification_open_metatada_success_title
                        } else {
                            R.string.notification_open_metatada_error_title
                        }
                    )
                ).setSubText(fileData.name)
                    .setContentText(result.humanDescription(resources))
            }
        )
    }

    /*
       On Android 6 emulator the stopSelfResult method return false even if provided key was the last one.
       It happened only if task was removed and onTaskRemoved method called.
       So I decided to calculate when it should be stopped by my own.
       */
    override fun onAddingFinished(id: Int, fileData: FileData) {
        if (presenter.tasksCount > 0) {
            stopSelf(id)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                //remove child notification from a group
                notificationManager.cancel(fileData.asNotificationId)
            } else {
                //refresh inbox notification
                notifyRootNotification()
            }
        } else {
            fullStop()
        }
    }

    override fun onAddingFailed(id: Int, fileData: FileData) {
        fullStop()
    }

    private fun notifyRootNotification() {
        notificationManager.notify(rootNotificationId, rootForegroundNotification())
    }

    private fun rootForegroundNotification() = newNotification {
        val comicsToOpen = presenter.allStartedAddComicData()

        setContentTitle(getString(R.string.notification_open_metadata_title))
            .apply {
                if (comicsToOpen.isNotEmpty()) {
                    setContentText(
                        if (comicsToOpen.size > 1) {
                            resources.getQuantityString(
                                R.plurals.notification_open_metadata_text,
                                comicsToOpen.size,
                                comicsToOpen.size
                            )
                        } else {
                            comicsToOpen.first().name
                        }
                    )
                }
            }
            .setGroup(AppNotification.Group.OPEN_COMIC_BOOK_METADATA)
            .setGroupSummary(true)
            .apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    if (comicsToOpen.isNotEmpty()) {
                        setStyle(
                            NotificationCompat.InboxStyle().also { inboxStyle ->
                                comicsToOpen
                                    .take(MAX_INBOX_NOTIFICATIONS)
                                    .forEach { (_, name, _) -> inboxStyle.addLine(name) }

                                if (comicsToOpen.size > MAX_INBOX_NOTIFICATIONS) {
                                    inboxStyle.setSummaryText(
                                        getString(
                                            R.string.notification_open_metadata_more,
                                            comicsToOpen.size - MAX_INBOX_NOTIFICATIONS
                                        )
                                    )
                                }
                            }
                        )
                    }

                    addCancelAction(this@AddComicBookService)
                }
            }
    }

    private class BinderImpl(
        private val context: Context,
        val presenter: AddComicBookPresenter
    ) : Binder(), AddComicBookServiceBinder {

        override fun subscribe() =
            presenter.subscribe()

        override suspend fun add(
            paths: List<Uri>,
            addComicBookMode: AddComicBookMode,
            openFlags: Int
        ): List<ComicAddResult> {
            if (paths.isEmpty()) {
                return emptyList()
            }

            paths.forEach { path ->
                ContextCompat.startForegroundService(
                    context,
                    openComicBookIntent(context, path, addComicBookMode, openFlags)
                )
            }

            val pathsSet = paths.toHashSet()

            return presenter.subscribe()
                .filter { pathsSet.remove(it.data.path) }
                .take(pathsSet.size)
                .toList()
        }

        override fun cancel(comicBookPath: Uri) =
            presenter.cancelAdding(comicBookPath)
    }

    companion object {
        private const val MAX_INBOX_NOTIFICATIONS = 5

        private const val ACTION_OPEN = "open"
        private const val ACTION_CANCEL = "cancel"

        private const val EXTRA_OPEN_MODE = "open_mode"

        private const val PENDING_CANCEL_REQUEST_CODE = 101

        private val FileData.asNotificationId: Int
            get() = hashCode()

        /**
         * Pass it to the [Context.startService]
         * After that will start opening comic book by it path
         * [AddComicBookService] can be bound using [AddComicBookServiceBinder]
         *
         * @param path path to the comic book
         */
        private fun openComicBookIntent(
            context: Context,
            path: Uri,
            addComicBookMode: AddComicBookMode,
            openFlags: Int
        ): Intent {
            //add flags to move read permissions from Activity to Service component
            return Intent(context, AddComicBookService::class.java)
                .setAction(ACTION_OPEN)
                .putExtra(EXTRA_OPEN_MODE, addComicBookMode)
                .setFlags(openFlags)
                .setData(path)
        }

        /**
         * Create pending intent for a cancel opening action
         *
         * @param context context
         * @param comicBookUri comic book path to cancel. All tasks will be cancelled of null
         * @return pending intent for a cancel opening action
         */
        private fun cancelOpeningPendingIntent(
            context: Context,
            comicBookUri: Uri? = null
        ): PendingIntent =
            PendingIntent.getService(
                context,
                PENDING_CANCEL_REQUEST_CODE,
                Intent(context, AddComicBookService::class.java)
                    .setAction(ACTION_CANCEL)
                    .setData(comicBookUri),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_ONE_SHOT
            )

        /**
         * Extract open mode from intent
         */
        private fun Intent.extractOpenMode(): AddComicBookMode {
            return requireNotNull(getSerializableExtra(EXTRA_OPEN_MODE)) as AddComicBookMode
        }

        private fun NotificationCompat.Builder.addCancelAction(
            context: Context,
            comicBookPath: Uri? = null
        ) = addAction(
            0,
            context.getString(R.string.all_cancel),
            cancelOpeningPendingIntent(context, comicBookPath)
        )
    }
}

interface AddComicBookServiceBinder {
    /**
     * Subscribe to all open comic book results
     * @return open comic book results flow
     */
    fun subscribe(): Flow<ComicAddResult>

    /**
     * Add comic book into user library by provided [path]
     *
     * @param path describes path to the comic book content
     * @return result of opening operation
     */
    suspend fun add(
        path: Uri,
        addComicBookMode: AddComicBookMode,
        openFlags: Int
    ): ComicAddResult {
        return add(Collections.singletonList(path), addComicBookMode, openFlags).first()
    }

    /**
     * Add comic books into user library by provided [paths]. Will suspend until all paths will be resolved
     *
     * @param paths describes paths to the comic books content
     * @return result of opening operation. Size of the output is equal to the input size
     */
    suspend fun add(
        paths: List<Uri>,
        addComicBookMode: AddComicBookMode,
        openFlags: Int
    ): List<ComicAddResult>

    /**
     * Cancel opening process
     *
     * @param comicBookPath comic book path which should be cancelled
     * @return true if job was cancelled
     */
    fun cancel(comicBookPath: Uri): Boolean
}