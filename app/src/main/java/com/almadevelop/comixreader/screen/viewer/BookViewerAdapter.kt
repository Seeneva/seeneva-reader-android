package com.almadevelop.comixreader.screen.viewer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.almadevelop.comixreader.logic.entity.ComicBookPage
import com.almadevelop.comixreader.screen.viewer.page.BookViewerPageFragment

/**
 * Adapter which display single comic book pages as Android Fragments
 */
class BookViewerAdapter(
    activity: FragmentActivity,
    initPages: List<ComicBookPage>? = null,
) : FragmentStateAdapter(activity) {
    private val differ = AsyncListDiffer(this, PageDiffCallback())

    /**
     * Pages ids
     */
    private val ids = hashSetOf<Long>()

    private var positionOverrideFun: ((count: Int, pos: Int) -> Int)? = null

    init {
        setPages(initPages)
    }

    override fun getItemCount() = differ.currentList.size

    override fun createFragment(position: Int): Fragment {
        return BookViewerPageFragment.newInstance(getPage(position).id)
    }

    override fun getItemId(position: Int) =
        if (position in differ.currentList.indices) {
            getPage(position).id
        } else {
            RecyclerView.NO_ID
        }


    //need to be implemented because I use custom getItemId. See description
    override fun containsItem(itemId: Long) = ids.contains(itemId)

    fun setPages(pages: List<ComicBookPage>?) {
        ids.clear()

        pages?.forEach { ids += it.id }

        differ.submitList(pages)
    }

    /**
     * Set function which will override adapter position
     */
    fun setPositionOverrideFun(f: ((count: Int, pos: Int) -> Int)?) {
        positionOverrideFun = f
        notifyDataSetChanged()
    }

    private fun getPage(pos: Int) =
        differ.currentList[positionOverrideFun?.invoke(itemCount, pos) ?: pos]
}