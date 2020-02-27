package com.almadevelop.comixreader.screen.list.selection

import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.widget.RecyclerView

class ComicIdSelectionProvider(private val recyclerView: RecyclerView) : ItemKeyProvider<Long>(SCOPE_MAPPED) {

    override fun getKey(position: Int): Long? {
        return recyclerView.adapter!!.getItemId(position).takeIf { it != RecyclerView.NO_ID }
    }

    override fun getPosition(key: Long): Int {
        return recyclerView.findViewHolderForItemId(key)?.adapterPosition ?: RecyclerView.NO_POSITION
    }
}