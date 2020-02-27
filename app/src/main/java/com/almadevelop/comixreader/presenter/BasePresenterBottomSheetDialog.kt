package com.almadevelop.comixreader.presenter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.almadevelop.comixreader.di.declareFragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.koin.androidx.scope.currentScope

abstract class BasePresenterBottomSheetDialog : BottomSheetDialogFragment(), PresenterStatefulView {
    override val presenterContext: Context
        get() = requireContext()

    protected abstract val presenter: ComponentPresenter

    init {
        @Suppress("LeakingThis")
        currentScope.declareFragment(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter.onViewCreated()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        presenter.onActivityResult(requestCode, resultCode, data)
    }
}