package com.almadevelop.comixreader.presenter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.savedstate.SavedStateRegistry
import com.almadevelop.comixreader.common.coroutines.Dispatched
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

interface Presenter

interface ComponentPresenter : Presenter {
    fun onViewCreated() {
        //do nothing
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        //do nothing
    }
}

abstract class BasePresenter<V : PresenterView>(
    protected val view: V,
    override val dispatchers: Dispatchers
) : Presenter, CoroutineScope, Dispatched {
    override val coroutineContext by lazy { Job() + dispatchers.main }

    protected val context: Context
        get() = view.presenterContext

    init {
        view.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                cancel()
            }
        })
    }

    protected inline fun <T> LiveData<T>.observe(crossinline observer: (T) -> Unit) {
        observe(view, Observer { observer(it) })
    }
}

abstract class BaseStatefulPresenter<V : PresenterStatefulView>(
    view: V,
    dispatchers: Dispatchers
) : BasePresenter<V>(view, dispatchers), SavedStateRegistry.SavedStateProvider {

    init {
        view.lifecycle.addObserver(object : DefaultLifecycleObserver {
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