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

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.seeneva.reader.R
import app.seeneva.reader.databinding.VhComicGridBinding
import app.seeneva.reader.databinding.VhComicGridPlaceholderBinding
import app.seeneva.reader.databinding.VhComicListBinding
import app.seeneva.reader.databinding.VhComicListPlaceholderBinding
import app.seeneva.reader.logic.ComicListViewType
import app.seeneva.reader.logic.entity.ComicListItem
import app.seeneva.reader.logic.image.ImageLoader
import app.seeneva.reader.widget.ComicCoverView
import app.seeneva.reader.widget.ComicItemView

class ComicsAdapter(
    private var comicViewType: ComicListViewType,
    private val imageLoader: ImageLoader,
    private val inflater: LayoutInflater,
    private val callback: Callback
) : PagingDataAdapter<ComicListItem, RecyclerView.ViewHolder>(ComicDiffUtilCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            R.layout.vh_comic_grid ->
                ComicsViewHolder.grid(parent, inflater, imageLoader, callback)
            R.layout.vh_comic_list ->
                ComicsViewHolder.list(parent, inflater, imageLoader, callback)
            R.layout.vh_comic_grid_placeholder ->
                Placeholder.grid(parent, inflater)
            R.layout.vh_comic_list_placeholder ->
                Placeholder.list(parent, inflater)
            else ->
                throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        if (payloads.contains(SelectionTracker.SELECTION_CHANGED_MARKER) && holder is ComicsViewHolder) {
            holder.onSelectionChanged(requireNotNull(peek(position)).isSelected())
        } else {
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
            ComicListViewType.GRID -> if (peek(position) == null) {
                R.layout.vh_comic_grid_placeholder
            } else {
                R.layout.vh_comic_grid
            }
            ComicListViewType.LIST -> if (peek(position) == null) {
                R.layout.vh_comic_list_placeholder
            } else {
                R.layout.vh_comic_list
            }
        }

    fun setComicViewType(newComicViewType: ComicListViewType) {
        if (comicViewType == newComicViewType) {
            return
        }

        comicViewType = newComicViewType

        notifyItemRangeChanged(0, itemCount)
    }

    private fun ComicListItem.isSelected() = callback.isItemSelected(this)

    class Placeholder private constructor(
        root: View,
        coverView: ComicCoverView,
    ) : RecyclerView.ViewHolder(root) {
        private val context
            get() = itemView.context

        init {
            coverView.setImageDrawable(ComicItemView.placeholderDrawable(context))
        }

        companion object {
            /**
             * @return placeholder for grid type
             */
            fun grid(parent: ViewGroup, inflater: LayoutInflater): Placeholder {
                val binding = VhComicGridPlaceholderBinding.inflate(inflater, parent, false)

                return Placeholder(binding.root, binding.coverLayout.cover)
            }

            /**
             * @return placeholder for list type
             */
            fun list(parent: ViewGroup, inflater: LayoutInflater): Placeholder {
                val binding = VhComicListPlaceholderBinding.inflate(inflater, parent, false)

                return Placeholder(binding.root, binding.coverLayout.cover)
            }
        }
    }

    class ComicsViewHolder private constructor(
        private val comicItemView: ComicItemView,
        imageLoader: ImageLoader,
        private val callback: Callback
    ) : RecyclerView.ViewHolder(comicItemView) {
        private lateinit var selectionDetails: ItemDetailsLookup.ItemDetails<Long>

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

            selectionDetails = object : ItemDetailsLookup.ItemDetails<Long>() {
                override fun getPosition() =
                    absoluteAdapterPosition

                override fun getSelectionKey() =
                    displayedComic.id
            }

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

        companion object {
            fun grid(
                parent: ViewGroup,
                inflater: LayoutInflater,
                imageLoader: ImageLoader,
                callback: Callback
            ): ComicsViewHolder {
                val binding = VhComicGridBinding.inflate(inflater, parent, false)

                return ComicsViewHolder(binding.root, imageLoader, callback)
            }

            fun list(
                parent: ViewGroup,
                inflater: LayoutInflater,
                imageLoader: ImageLoader,
                callback: Callback
            ): ComicsViewHolder {
                val binding = VhComicListBinding.inflate(inflater, parent, false)

                return ComicsViewHolder(binding.root, imageLoader, callback)
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