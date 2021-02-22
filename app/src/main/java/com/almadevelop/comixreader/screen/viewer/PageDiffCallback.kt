package com.almadevelop.comixreader.screen.viewer

import androidx.recyclerview.widget.DiffUtil
import com.almadevelop.comixreader.logic.entity.ComicBookPage

class PageDiffCallback : DiffUtil.ItemCallback<ComicBookPage>() {
    override fun areItemsTheSame(oldItem: ComicBookPage, newItem: ComicBookPage) =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: ComicBookPage, newItem: ComicBookPage) =
        oldItem == newItem
}