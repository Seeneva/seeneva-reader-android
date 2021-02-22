package com.almadevelop.comixreader.extension

import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * Return [ViewPager2] child [RecyclerView]
 * throw [IllegalStateException] if there is no child [RecyclerView]
 */
val ViewPager2.recyclerView
    get() = checkNotNull(
        children
            .filterIsInstance<RecyclerView>()
            .firstOrNull()
    ) { "ViewPager, what is wrong with you? Where is RecyclerView?" }