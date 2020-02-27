package com.almadevelop.comixreader.presenter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.almadevelop.comixreader.di.declareFragment
import org.koin.androidx.scope.currentScope

abstract class BasePresenterFragment : Fragment, PresenterStatefulView {
    override val presenterContext: Context
        get() = requireContext()

    protected abstract val presenter: ComponentPresenter

    constructor() : super()
    constructor(@LayoutRes contentLayoutId: Int) : super(contentLayoutId)

    init {
        @Suppress("LeakingThis")
        currentScope.declareFragment(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prepareView(savedInstanceState)
        presenter.onViewCreated()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        presenter.onActivityResult(requestCode, resultCode, data)
    }

    protected abstract fun prepareView(savedInstanceState: Bundle?)
}