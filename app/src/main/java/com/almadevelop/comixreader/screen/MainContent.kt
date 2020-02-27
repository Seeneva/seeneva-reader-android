package com.almadevelop.comixreader.screen

import android.view.View
import android.view.ViewGroup

interface MainContent {
    /**
     * Add ActionBar child view
     * @param parent parent view
     * @return ActionBar content
     */
    fun activityActionBarContent(parent: ViewGroup): View? {
        return null
    }
}