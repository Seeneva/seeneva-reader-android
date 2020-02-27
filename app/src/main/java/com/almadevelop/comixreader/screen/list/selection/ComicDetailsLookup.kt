package com.almadevelop.comixreader.screen.list.selection

import android.view.MotionEvent
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.widget.RecyclerView
import com.almadevelop.comixreader.screen.list.adapter.ComicsAdapter

class ComicDetailsLookup(private val recyclerView: RecyclerView) : ItemDetailsLookup<Long>() {
    override fun getItemDetails(e: MotionEvent): ItemDetails<Long>? {
        return recyclerView.findChildViewUnder(e.x, e.y)
            ?.let { (recyclerView.getChildViewHolder(it) as? ComicsAdapter.ComicsViewHolder)?.getSelectionDetails(e) }
    }
}