package com.almadevelop.comixreader.presenter

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Base presenter view which can observe lifecycle
 */
interface PresenterView : LifecycleOwner {
    val presenterContext: Context
}

/**
 * Base presenter view which can save states
 */
interface PresenterStatefulView: PresenterView, SavedStateRegistryOwner