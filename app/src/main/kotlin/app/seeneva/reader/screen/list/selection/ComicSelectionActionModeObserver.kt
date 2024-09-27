/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
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

package app.seeneva.reader.screen.list.selection

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.flowWithLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.selection.SelectionTracker
import app.seeneva.reader.R
import app.seeneva.reader.screen.list.adapter.ComicsAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Start action mode if any comic book were selected
 */
class ComicSelectionActionModeObserver(
    private val adapter: ComicsAdapter,
    private val selectionTracker: SelectionTracker<Long>,
    private val lifecycle: Lifecycle,
    private val actionModeCallback: ActionModeCallback,
) : SelectionTracker.SelectionObserver<Long>() {
    private val selectChangeChannel = Channel<Unit>(Channel.CONFLATED)

    /**
     * Job of ActionMode invalidator
     */
    private var actionModeJob: Job? = null

    private var actionMode: ActionMode? = null

    private val actionModeCallbackInner = object : ActionMode.Callback {
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.remove -> {
                    actionModeCallback.onMarkAsRemovedSelectedClick()
                    finishActionMode()
                    true
                }

                R.id.change_complete_state -> {
                    val allCompleted = mode.allSelectedCompleted

                    actionModeCallback.onMarkAsCompletedSelectedClick(!allCompleted)
                    finishActionMode()
                    true
                }

                else -> false
            }
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.comics_list_select, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.setSelectedCount()

            menu.findItem(R.id.change_complete_state)
                .setTitle(
                    if (mode.allSelectedCompleted) {
                        R.string.comic_list_not_completed
                    } else {
                        R.string.comic_list_completed
                    }
                )

            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            finishActionMode()
        }
    }

    override fun onSelectionRefresh() {
        super.onSelectionRefresh()
        if (!selectionTracker.hasSelection()) {
            finishActionMode()
        }
    }

    override fun onSelectionRestored() {
        super.onSelectionRestored()
        if (selectionTracker.hasSelection()) {
            startActionMode()
        }
    }

    override fun onItemStateChanged(key: Long, selected: Boolean) {
        super.onItemStateChanged(key, selected)
        when {
            selected && actionMode == null -> startActionMode()
            !selected && !selectionTracker.hasSelection() -> finishActionMode()
            else -> selectChangeChannel.trySend(Unit)
        }
    }

    private fun startActionMode() {
        if (actionModeJob?.isActive == true) {
            return
        }

        actionModeJob = lifecycle.coroutineScope.launch {
            comicsCompletionFlow().combine(selectionChangeSignal()) { idToCompletion, _ ->
                // Iterate over all selected ids and check is any of them has not_completed state
                // Otherwise all books are completed
                selectionTracker.selection.firstNotNullOfOrNull {
                    if (idToCompletion[it] == false) {
                        false
                    } else {
                        null
                    }
                } ?: true
            }.onCompletion {
                actionMode?.finish()
                actionMode = null

                selectionTracker.clearSelection()
            }.flowWithLifecycle(lifecycle)
                .collect { allSelectedCompleted ->
                    val actionMode = actionMode ?: actionModeCallback.start(actionModeCallbackInner)
                        .also { actionMode = it }

                    if (actionMode != null) {
                        actionMode.tag = allSelectedCompleted

                        actionMode.invalidate()
                    } else {
                        // nothing to do here
                        finishActionMode()
                    }
                }
        }
    }

    private fun finishActionMode() {
        actionModeJob?.cancel()
        actionModeJob = null
    }

    /**
     * @return Flow which emit all loaded comic books completion state
     */
    private fun comicsCompletionFlow() =
        adapter.loadStateFlow
            .filter {
                it.refresh is LoadState.NotLoading &&
                        it.append is LoadState.NotLoading &&
                        it.prepend is LoadState.NotLoading
            }
            .map {
                // Get all loaded items and build map of bookId -> completed_or_not
                adapter.snapshot()
                    .items
                    .associateByTo(
                        hashMapOf(),
                        { it.id },
                        { it.completed })
            }

    /**
     * @return Flow which emit signals that selection was changed
     */
    private fun selectionChangeSignal() =
        selectChangeChannel.receiveAsFlow().onStart { emit(Unit) }

    private fun ActionMode.setSelectedCount() {
        title = selectionTracker.selection.size().toString()
    }

    interface ActionModeCallback {
        /**
         * Start [ActionMode]
         */
        fun start(callback: ActionMode.Callback): ActionMode?

        /**
         * On mark as removed all selected comics click
         */
        fun onMarkAsRemovedSelectedClick()

        /**
         * On mark as completed all selected comics click
         * @param completed is comics should be marked as completed
         */
        fun onMarkAsCompletedSelectedClick(completed: Boolean)
    }

    private companion object {
        /**
         * true if all selected comic books are in completed state
         */
        private val ActionMode.allSelectedCompleted
            get() = tag as? Boolean ?: false
    }
}