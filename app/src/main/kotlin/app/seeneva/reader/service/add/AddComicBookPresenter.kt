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

package app.seeneva.reader.service.add

import android.net.Uri
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.common.entity.FileHashData
import app.seeneva.reader.logic.comic.AddComicBookMode
import app.seeneva.reader.logic.comic.Library
import app.seeneva.reader.logic.entity.ComicAddResult
import app.seeneva.reader.logic.entity.FileData
import app.seeneva.reader.logic.entity.FullFileData
import app.seeneva.reader.logic.usecase.FileDataUseCase
import app.seeneva.reader.presenter.BasePresenter
import app.seeneva.reader.presenter.Presenter
import app.seeneva.reader.viewmodel.EventSender
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

/**
 * Public interface of the presenter
 */
interface AddComicBookPresenter : Presenter {
    /**
     * Count of all started tasks
     */
    val tasksCount: Int

    /**
     * Subscribes into adding result events
     */
    val resultsFlow: Flow<ComicAddResult>

    /**
     * Add comic book into user library by provided [path]
     *
     * @param id id of the opening task
     * @param path used to open comic book
     * @param addComicBookMode adding mode
     * @return true if [path] was accepted and task has been started
     */
    fun add(id: Int, path: Uri, addComicBookMode: AddComicBookMode): Boolean

    /**
     * @return all comic book data which add task is started
     */
    fun allStartedAddComicData(): List<FileData>

    /**
     * Cancel comic book opening task
     * @param comicBookPath path of the comic book to cancel. Cancel all tasks if null
     * @return true if task was cancelled
     */
    fun cancelAdding(comicBookPath: Uri? = null): Boolean
}

class AddComicBookPresenterImpl(
    view: AddComicBookView,
    fileDataUseCase: FileDataUseCase,
    library: Library,
    dispatchers: Dispatchers
) : BasePresenter<AddComicBookView>(view, dispatchers), AddComicBookPresenter {
    private val tasks = Tasks(view, fileDataUseCase, library, presenterScope)

    override val tasksCount: Int
        get() = tasks.count

    override val resultsFlow
        get() = tasks.resultFlow

    override fun add(
        id: Int,
        path: Uri,
        addComicBookMode: AddComicBookMode
    ): Boolean {
        return tasks.runTask(id, path, addComicBookMode)
    }

    override fun allStartedAddComicData(): List<FileData> {
        return tasks.all()
    }

    override fun cancelAdding(comicBookPath: Uri?): Boolean {
        //if not null - cancel specific job
        return if (comicBookPath != null) {
            tasks.cancel(comicBookPath)
        } else {
            //cancel all opening children jobs
            requireNotNull(presenterScope.coroutineContext[Job]).cancelChildren()

            true
        }
    }

    //call every method from the same thread! e.g Main Thread
    private class Tasks(
        private val view: AddComicBookView,
        private val fileDataUseCase: FileDataUseCase,
        private val library: Library,
        private val scope: CoroutineScope
    ) {
        private val resultSender = EventSender<ComicAddResult>()

        /**
         * All open tasks
         */
        private val openTasks = hashMapOf<Uri, OpenTask>()

        /**
         * File hash to file path. Needed to prevent open the same file twice
         */
        private val hashes = hashMapOf<FileHashData, Uri>()

        val resultFlow
            get() = resultSender.eventState

        val count: Int
            get() = openTasks.size

        fun all(): List<FileData> {
            return if (openTasks.isEmpty()) {
                emptyList()
            } else {
                openTasks.values.map { it.fileData }
            }
        }

        /**
         * 1. Get file data
         * 2. Check if the same file is already opening
         * 3. Cancel if it is already opening (I want to prevent books duplicates in the user library)
         * 4. Continue opening if not
         */
        fun runTask(taskId: Int, path: Uri, addMode: AddComicBookMode): Boolean {
            if (openTasks.containsKey(path)) {
                return false
            }

            scope.launch {
                //I decided to split file data receiving and file hash calculation to show notifications as soon as possible
                val fileData = fileDataUseCase.getFileData(path)

                openTasks[path] = OpenTask(checkNotNull(coroutineContext[Job]), fileData, taskId)

                //notify view about new task to show notifications
                //and don't wait while file's hash will be calculated
                view.onAddingStarted(fileData)

                //function to remove task from cache and notify view about it
                val clearCacheNotify: () -> Unit = {
                    val task = remove(path)

                    view.onAddingFinished(task.id, fileData)
                }

                val fileHashData = runCatching { fileDataUseCase.getFileHashData(path) }
                    .onFailure {
                        //In case of error (well...cancellation only) clear cache and notify view
                        if (it is CancellationException) {
                            clearCacheNotify()
                        }
                    }.getOrThrow()

                if (hashes.containsKey(fileHashData)) {
                    //we already has that file in the adding tasks
                    clearCacheNotify()
                } else {

                    //it is fully new adding
                    processAdding(
                        fileData,
                        fileHashData,
                        addMode,
                    )
                }
            }

            return true
        }

        /**
         * Cancel task by comic book [path]
         * @param path comic book path
         * @return true if it was cancelled
         */
        fun cancel(path: Uri): Boolean {
            return openTasks[path]?.let {
                it.job.cancel()

                true
            } ?: false
        }

        private fun remove(path: Uri): OpenTask =
            requireNotNull(openTasks.remove(path)) { "Can't find task by path: '$path'" }

        private fun remove(fileHash: FileHashData): OpenTask =
            remove(requireNotNull(hashes.remove(fileHash)) { "Can't find task by hash" })

        private suspend fun processAdding(
            fileData: FileData,
            fileHashData: FileHashData,
            addMode: AddComicBookMode,
        ) {
            hashes[fileHashData] = fileData.path

            val result = try {
                val fullFileData = FullFileData(
                    fileData.path,
                    fileData.name,
                    fileHashData.size,
                    fileHashData.hash
                )

                library.add(fullFileData, addMode).also { resultSender.send(it) }
            } finally {
                //Any case we should clear cache and notify view about error/cancellation
                val task = remove(fileHashData)

                view.onAddingFinished(task.id, fileData)
            }

            currentCoroutineContext().ensureActive()

            view.onAddingResult(fileData, result)
        }
    }

    private data class OpenTask(val job: Job, val fileData: FileData, val id: Int)
}