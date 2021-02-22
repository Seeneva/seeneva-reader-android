package com.almadevelop.comixreader.presenter

import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistry
import com.almadevelop.comixreader.common.coroutines.Dispatched
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import androidx.lifecycle.lifecycleScope as lifecycleCoroutineScope

interface Presenter

abstract class BasePresenter<V : PresenterView>(
    protected val view: V,
    final override val dispatchers: Dispatchers
) : Presenter, Dispatched {
    protected val presenterScope =
        CoroutineScope(Job(view.lifecycleCoroutineScope.coroutineContext.job) + dispatchers.main)

    protected val viewScope
        get() = view.lifecycleCoroutineScope

    protected val viewLifeCycle
        get() = view.lifecycle
}

abstract class BaseStatefulPresenter<V : PresenterStatefulView>(
    view: V,
    dispatchers: Dispatchers
) : BasePresenter<V>(view, dispatchers), SavedStateRegistry.SavedStateProvider {

    init {
        viewLifeCycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                val key = this@BaseStatefulPresenter.javaClass.name

                //register presenter as state provider and try to restore previous state
                with(view.savedStateRegistry) {
                    registerSavedStateProvider(key, this@BaseStatefulPresenter)

                    onCreate(consumeRestoredStateForKey(key))
                }
            }
        })
    }

    protected abstract fun onCreate(state: Bundle?)
}