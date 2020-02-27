package com.almadevelop.comixreader.screen.list.selection

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.logic.entity.ComicListItem
import com.almadevelop.comixreader.screen.list.adapter.ComicListObserver
import com.almadevelop.comixreader.screen.list.adapter.ComicsAdapter
import java.lang.ref.WeakReference
import java.util.*

/**
 * Start action mode if any comic book were selected
 */
class ComicSelectionActionModeObserver(
    activity: AppCompatActivity,
    private val adapter: ComicsAdapter,
    private val selectionTracker: SelectionTracker<Long>,
    private val actionModeCallback: ActionModeCallback
) : SelectionTracker.SelectionObserver<Long>() {
    //used as workaround of removing selection if item was removed from a RecyclerView
    //[androidx.recyclerview.selection.SelectionTracker.SelectionObserver] not called when you call [notifyItemRemoved]
    //Or maybe I miss something :(
    private var onComicListChanged: ((List<ComicListItem>?) -> Unit)? = null

    private val activityWeak = WeakReference(activity)

    private var actionMode: ActionMode? = null

    private val observer: ComicListObserver = { previousList, currentList ->
        onComicListChanged?.invoke(previousList)
        onComicListChanged = null

        if (selectionTracker.hasSelection()) {
            actionMode.also { actionMode ->
                requireNotNull(actionMode) { "ActionMode cannot be null" }

                actionMode.tag = ComicActionModeTag(
                    currentList?.associateByTo(hashMapOf(), { it.id }, { it.completed })
                        ?: Collections.emptyMap()
                )

                actionMode.invalidate()
            }
        }
    }

    init {
        adapter.registerAdapterDataObserver(ComicDataObserver())
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
            val activity = activityWeak.get()

            requireNotNull(activity) { "Activity is null" }

            actionMode = activity.startSupportActionMode(ComicActionModeCallback())

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

    private fun ActionMode.setSelectedCount() {
        title = selectionTracker.selection.size().toString()
    }


    private fun ComicActionModeTag?.isAllCompleted(): Boolean {
        return if (this != null) {
            allCompleted
                ?: (!idToCompleted.filter { selectionTracker.isSelected(it.key) }
                    .containsValue(false))
                    .also { allCompleted = it }
        } else {
            false
        }
    }

    private data class ComicActionModeTag(val idToCompleted: Map<Long, Boolean>, var allCompleted: Boolean? = null)

    private inner class ComicDataObserver : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            super.onItemRangeRemoved(positionStart, itemCount)

            onComicListChanged = { comics ->
                if (!comics.isNullOrEmpty()) {
                    //deselect any removed comic book
                    (positionStart until positionStart + itemCount).map { comics[it] }.forEach {
                        selectionTracker.deselect(it.id)
                    }
                }
            }
        }
    }

    private inner class ComicActionModeCallback : ActionMode.Callback {
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

    interface ActionModeCallback {
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