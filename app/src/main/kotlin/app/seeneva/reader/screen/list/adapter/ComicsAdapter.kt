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

package app.seeneva.reader.screen.list.adapter

import android.view.MotionEvent
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.seeneva.reader.R
import app.seeneva.reader.extension.inflate
import app.seeneva.reader.logic.ComicListViewType
import app.seeneva.reader.logic.entity.ComicListItem
import app.seeneva.reader.logic.image.ImageLoader
import app.seeneva.reader.widget.ComicCoverView
import app.seeneva.reader.widget.ComicItemView
import java.lang.ref.WeakReference

typealias ComicListObserver = (previousList: List<ComicListItem?>?, currentList: List<ComicListItem?>?) -> Unit

class ComicsAdapter(
    private var comicViewType: ComicListViewType,
    private val imageLoader: ImageLoader,
    private val callback: Callback
) : PagedListAdapter<ComicListItem, RecyclerView.ViewHolder>(ComicDiffUtilCallback()) {
    //observer of the diff utils [onCurrentListChanged]
    private val currentListObservers: MutableList<WeakReference<ComicListObserver>> = arrayListOf()

    init {
        //we need it to use [app.seeneva.reader.ComicIdSelectionProvider]
        //[RecyclerView.findViewHolderForItemId]
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            R.layout.vh_comic_grid, R.layout.vh_comic_list ->
                ComicsViewHolder(parent, viewType, imageLoader, callback)
            R.layout.vh_comic_grid_placeholder, R.layout.vh_comic_list_placeholder ->
                Placeholder(parent, viewType)
            else ->
                throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        val handled =
            if (payloads.contains(SelectionTracker.SELECTION_CHANGED_MARKER) && holder is ComicsViewHolder) {
                holder.onSelectionChanged(requireNotNull(getItem(position)).isSelected())

                true
            } else {
                false
            }

        if (!handled) {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ComicsViewHolder -> {
                val comicItem = getItem(position)!!

                holder.bind(comicItem, comicItem.isSelected())
            }
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is ComicsViewHolder) {
            holder.onAttachChange(true)
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is ComicsViewHolder) {
            holder.onAttachChange(false)
        }
    }

    override fun getItemViewType(position: Int) =
        when (comicViewType) {
            ComicListViewType.GRID -> if (getItem(position) == null) {
                R.layout.vh_comic_grid_placeholder
            } else {
                R.layout.vh_comic_grid
            }
            ComicListViewType.LIST -> if (getItem(position) == null) {
                R.layout.vh_comic_list_placeholder
            } else {
                R.layout.vh_comic_list
            }
        }

    override fun onCurrentListChanged(
        previousList: PagedList<ComicListItem>?,
        currentList: PagedList<ComicListItem>?
    ) {
        super.onCurrentListChanged(previousList, currentList)
        notifyListObservers(previousList, currentList)
    }

    override fun getItemId(position: Int) =
        //just set id for placeholders as negative position
        getItem(position)?.id
            ?: (-position.dec()
                .toLong()).let { if (it == RecyclerView.NO_ID) Long.MIN_VALUE else it }

    /**
     * Add observers which would be notified after DiffUtil finished
     * @param observer function which will be called
     */
    fun addWeakCurrentListObserver(observer: ComicListObserver) {
        currentListObservers += WeakReference(observer)
    }

    fun removeWeakCurrentListObserver(observer: ComicListObserver) {
        val iterator = currentListObservers.iterator()

        while (iterator.hasNext()) {
            val currentObserver = iterator.next().get()

            if (currentObserver == null) {
                iterator.remove()
            } else if (currentObserver === observer) {
                iterator.remove()
                break
            }
        }
    }

    fun setComicViewType(newComicViewType: ComicListViewType) {
        if (comicViewType == newComicViewType) {
            return
        }

        comicViewType = newComicViewType

        notifyItemRangeChanged(0, itemCount)
    }

    private fun notifyListObservers(
        previousList: PagedList<ComicListItem>?,
        currentList: PagedList<ComicListItem>?
    ) {
        if (currentListObservers.isEmpty()) {
            return
        }

        val iterator = currentListObservers.iterator()

        while (iterator.hasNext()) {
            val observer = iterator.next().get()

            if (observer != null) {
                observer(previousList, currentList)
            } else {
                iterator.remove()
            }
        }
    }

    private fun ComicListItem.isSelected() = callback.isItemSelected(this)

    class Placeholder(
        parent: ViewGroup,
        @LayoutRes layout: Int,
    ) : RecyclerView.ViewHolder(parent.inflate(layout)) {

        private val coverView = itemView.findViewById<ComicCoverView>(R.id.cover)

        private val context
            get() = itemView.context

        init {
            coverView.setImageDrawable(ComicItemView.placeholderDrawable(context))
        }
    }

    class ComicsViewHolder(
        parent: ViewGroup,
        @LayoutRes layout: Int,
        imageLoader: ImageLoader,
        private val callback: Callback
    ) : RecyclerView.ViewHolder(parent.inflate(layout)) {
        private val selectionDetails: ItemDetailsLookup.ItemDetails<Long>
            get() = SelectionDetails(displayedComic.id, absoluteAdapterPosition)

        private val comicItemView: ComicItemView
            get() = itemView as ComicItemView

        private lateinit var displayedComic: ComicListItem

        init {
            comicItemView.setOnActionsClickListener(object : ComicItemView.OnActionsClickListener {

                override fun onActionClick(action: ComicItemView.Action) {
                    when (action) {
                        ComicItemView.Action.INFO ->
                            callback.onItemInfoClick(displayedComic)
                        ComicItemView.Action.DELETE ->
                            callback.onItemDeleteClick(displayedComic)
                        ComicItemView.Action.RENAME ->
                            callback.onItemRenameClick(displayedComic)
                        ComicItemView.Action.MARK_READ ->
                            callback.onMarkAsReadClick(displayedComic)
                    }
                }
            })

            comicItemView.setOnClickListener { callback.onComicBookClick(displayedComic) }

            comicItemView.setImageLoader(imageLoader)
        }

        fun bind(comic: ComicListItem, selected: Boolean) {
            displayedComic = comic

            comicItemView.isActivated = selected
        }

        fun onAttachChange(attached: Boolean) {
            comicItemView.apply {
                //we need to cancel image loading on detach
                //and show again comic book on attach
                if (!attached) {
                    onDetach()
                } else {
                    showComicBook(displayedComic)
                }
            }
        }

        fun onSelectionChanged(selected: Boolean) {
            comicItemView.isActivated = selected
        }

        fun getSelectionDetails(e: MotionEvent): ItemDetailsLookup.ItemDetails<Long>? {
            return if (comicItemView.iSelectionZone(e.rawX, e.rawY)) {
                selectionDetails
            } else {
                null
            }
        }

        private data class SelectionDetails(
            private val id: Long,
            private val position: Int
        ) : ItemDetailsLookup.ItemDetails<Long>() {
            override fun getSelectionKey(): Long {
                return id
            }

            override fun getPosition(): Int {
                return position
            }
        }
    }

    interface Callback {
        fun onItemDeleteClick(comic: ComicListItem)

        fun onItemRenameClick(comic: ComicListItem)

        fun onItemInfoClick(comic: ComicListItem)

        fun onMarkAsReadClick(comic: ComicListItem)

        fun isItemSelected(comic: ComicListItem): Boolean

        /**
         * User click on comic book item
         */
        fun onComicBookClick(comic: ComicListItem)
    }

    private class ComicDiffUtilCallback : DiffUtil.ItemCallback<ComicListItem>() {
        override fun areItemsTheSame(oldItem: ComicListItem, newItem: ComicListItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ComicListItem, newItem: ComicListItem): Boolean {
            return oldItem == newItem
        }
    }
}