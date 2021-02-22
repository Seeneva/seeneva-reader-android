package com.almadevelop.comixreader.presenter

import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Base presenter view which can observe lifecycle
 */
interface PresenterView : LifecycleOwner

/**
 * Base presenter view which can save states
 */
interface PresenterStatefulView: PresenterView, SavedStateRegistryOwner