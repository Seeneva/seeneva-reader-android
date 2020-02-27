package com.almadevelop.comixreader.presenter

import android.content.Context
import androidx.lifecycle.LifecycleService

abstract class BasePresenterService : LifecycleService(), PresenterView {
    override val presenterContext: Context
        get() = this

    protected abstract val presenter: Presenter
}