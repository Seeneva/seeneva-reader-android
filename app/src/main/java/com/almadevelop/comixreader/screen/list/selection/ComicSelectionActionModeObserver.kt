package com.almadevelop.comixreader.screen.list.selection

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.selection.SelectionTracker
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.screen.list.adapter.ComicListObserver
import com.almadevelop.comixreader.screen.list.adapter.ComicsAdapter
import java.util.*

/**
 * Start action mode if any comic book were selected
 */
class ComicSelectionActionModeObserver(
    private val adapter: ComicsAdapter,
    private val selectionTracker: SelectionTracker<Long>,
    private val actionModeCallback: ActionModeCallback
) : SelectionTracker.SelectionObserver<Long>() {
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
                    val allCompleted = mode.comicsTag?.allCompleted

                    requireNotNull(allCompleted) { "Can't get ActionMode tag" }

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

            menu.findItem(R.id.change_complete_state).setTitle(
                if (mode.comicsTag.isAllCompleted()) {
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

    private val observer: ComicListObserver = { _, currentList ->
        if (selectionTracker.hasSelection()) {
            actionMode.also { actionMode ->
                requireNotNull(actionMode) { "ActionMode cannot be null" }

                actionMode.tag = ComicActionModeTag(
                    currentList?.filterNotNull()
                        ?.associateByTo(hashMapOf(), { it.id }, { it.completed })
                        ?: Collections.emptyMap()
                )

                actionMode.invalidate()
            }
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
            else -> {
                actionMode?.also {
                    //clear to recalculate later
                    it.comicsTag!!.allCompleted = null
                    it.invalidate()
                }
            }
        }
    }

    private fun startActionMode() {
        if (actionMode == null) {
            actionMode = actionModeCallback.start(actionModeCallbackInner)

            observer(null, adapter.currentList)
            adapter.addWeakCurrentListObserver(observer)
        }
    }

    private fun finishActionMode() {
        if (actionMode != null) {
            actionMode?.finish()
            actionMode = null
            adapter.removeWeakCurrentListObserver(observer)

            selectionTracker.clearSelection()
        }
    }

    private fun ComicActionModeTag?.isAllCompleted(): Boolean {
        return if (this == null) {
            false
        } else {
            allCompleted
                ?: (!idToCompleted.filter { selectionTracker.isSelected(it.key) }
                    .containsValue(false))
                    .also { allCompleted = it }
        }
    }

    private fun ActionMode.setSelectedCount() {
        title = selectionTracker.selection.size().toString()
    }

    private data class ComicActionModeTag(
        val idToCompleted: Map<Long, Boolean>,
        var allCompleted: Boolean? = null
    )

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
        private val ActionMode.comicsTag: ComicActionModeTag?
            get() = tag as ComicActionModeTag?
    }
}