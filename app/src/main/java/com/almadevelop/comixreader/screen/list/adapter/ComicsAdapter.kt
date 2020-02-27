package com.almadevelop.comixreader.screen.list.adapter

import android.view.MotionEvent
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.extension.inflate
import com.almadevelop.comixreader.logic.ComicListViewType
import com.almadevelop.comixreader.logic.entity.ComicListItem
import com.almadevelop.comixreader.screen.list.ListState
import com.almadevelop.comixreader.widget.ComicThumbView
import java.lang.ref.WeakReference

typealias ComicListObserver = (previousList: List<ComicListItem>?, currentList: List<ComicListItem>?) -> Unit

class ComicsAdapter(
    private var comicViewType: ComicListViewType,
    private val callback: Callback
) : PagedListAdapter<ComicListItem, RecyclerView.ViewHolder>(ComicDiffUtilCallback()) {
    //observer of the diff utils [onCurrentListChanged]
    private val currentListObservers: MutableList<WeakReference<ComicListObserver>> = arrayListOf()

    //it can be different viewTypes, but not for now :)
    /**
     * Begin offset from the real data. It can be progress bars and etc.
     */
    private var beginOffset = 0
    /**
     * End offset from the real data
     */
    private var endOffset = 0

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            R.layout.vh_comics_grid -> ComicsGridViewHolder(
                parent,
                callback
            )
            R.layout.vh_comic_list -> ComicsListViewHolder(
                parent,
                callback
            )
            R.layout.vh_comics_progress -> ProgressViewHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ComicsViewHolder -> {
                val item = requireNotNull(getItem(position - beginOffset))

                holder.bind(item, item.isSelected())
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ComicsViewHolder) {
            holder.unbind()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isLoaderPosition(position)) {
            R.layout.vh_comics_progress
        } else {
            when (comicViewType) {
                ComicListViewType.Grid -> R.layout.vh_comics_grid
                ComicListViewType.List -> R.layout.vh_comic_list
            }
        }
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + beginOffset + endOffset
    }

    override fun onCurrentListChanged(
        previousList: PagedList<ComicListItem>?,
        currentList: PagedList<ComicListItem>?
    ) {
        super.onCurrentListChanged(previousList, currentList)
        notifyListObservers(previousList, currentList)
    }

    override fun getItemId(position: Int): Long {
        return if (isLoaderPosition(position)) {
            RecyclerView.NO_ID
        } else {
            getItem(position - beginOffset)?.id ?: RecyclerView.NO_ID
        }
    }

    override fun submitList(pagedList: PagedList<ComicListItem>?) {
        throw UnsupportedOperationException()
    }

    override fun submitList(pagedList: PagedList<ComicListItem>?, commitCallback: Runnable?) {
        throw UnsupportedOperationException()
    }

    /**
     * @return is comic book loader indicator located by position
     */
    fun isLoaderPosition(position: Int): Boolean {
        return when (position) {
            in (0 until beginOffset), in (itemCount - endOffset until itemCount) -> true
            else -> false
        }
    }

    fun submitList(pagedList: PagedList<ComicListItem>, listState: ListState) {
        while (beginOffset > 0) {
            beginOffset--
            notifyItemRemoved(0)
        }

        while (endOffset > 0) {
            endOffset--
            notifyItemRemoved(itemCount)
        }

        super.submitList(pagedList) { updateListState(listState) }
    }

    fun updateListState(listState: ListState) {
        if (!listState.frontReached) {
            if (beginOffset == 0) {
                notifyItemInserted(beginOffset++)
            }
        } else {
            if (beginOffset > 0) {
                notifyItemRemoved(--beginOffset)
            }
        }

        if (!listState.endReached) {
            if (endOffset == 0) {
                endOffset++
                notifyItemInserted(itemCount)
            }
        } else {
            if (endOffset > 0) {
                notifyItemRemoved(itemCount)
                endOffset--
            }
        }
    }

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

    class ProgressViewHolder(parent: ViewGroup) :
        RecyclerView.ViewHolder(parent.inflate(R.layout.vh_comics_progress))

    abstract class ComicsViewHolder(
        parent: ViewGroup,
        @LayoutRes layout: Int,
        private val callback: Callback
    ) : RecyclerView.ViewHolder(parent.inflate(layout)) {
        private val selectionDetails: ItemDetailsLookup.ItemDetails<Long>
            get() = SelectionDetails(displayedComic.id, adapterPosition)

        private val thumbnailView: ComicThumbView
            get() = itemView as ComicThumbView

        private lateinit var displayedComic: ComicListItem

        init {
            thumbnailView.setOnActionsClickListener(object : ComicThumbView.OnActionsClickListener {
                override fun onActionClick(action: ComicThumbView.Action) {
                    when (action) {
                        ComicThumbView.Action.INFO -> callback.onItemInfoClick(displayedComic)
                        ComicThumbView.Action.DELETE -> callback.onItemDeleteClick(displayedComic)
                        ComicThumbView.Action.RENAME -> callback.onItemRenameClick(displayedComic)
                        ComicThumbView.Action.MARK_READ -> callback.onMarkAsReadClick(displayedComic)
                    }
                }
            })
        }

        fun bind(comic: ComicListItem, selected: Boolean) {
            displayedComic = comic

            thumbnailView.also {
                it.isActivated = selected

                it.showComicBook(displayedComic)
            }
        }

        fun unbind() {
            thumbnailView.cancelImageLoading()
        }

        fun getSelectionDetails(e: MotionEvent): ItemDetailsLookup.ItemDetails<Long>? {
            return if (thumbnailView.iSelectionZone(e.rawX, e.rawY)) {
                selectionDetails
            } else {
                null
            }
        }

        private data class SelectionDetails(
            private val id: Long,
            private val position: Int
        ) : ItemDetailsLookup.ItemDetails<Long>() {
            override fun getSelectionKey(): Long? {
                return id
            }

            override fun getPosition(): Int {
                return position
            }
        }
    }

    class ComicsGridViewHolder(parent: ViewGroup, callback: Callback) :
        ComicsViewHolder(parent, R.layout.vh_comics_grid, callback)

    class ComicsListViewHolder(parent: ViewGroup, callback: Callback) :
        ComicsViewHolder(parent, R.layout.vh_comic_list, callback)

    interface Callback {
        fun onItemDeleteClick(comic: ComicListItem)

        fun onItemRenameClick(comic: ComicListItem)

        fun onItemInfoClick(comic: ComicListItem)

        fun onMarkAsReadClick(comic: ComicListItem)

        fun isItemSelected(comic: ComicListItem): Boolean
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