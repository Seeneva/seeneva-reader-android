package com.almadevelop.comixreader.presenter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import com.almadevelop.comixreader.di.declareActivity
import org.koin.androidx.scope.currentScope

abstract class BasePresenterActivity : AppCompatActivity, PresenterStatefulView {
    override val presenterContext: Context
        get() = this

    protected abstract val presenter: ComponentPresenter

    constructor() : super()
    constructor(@LayoutRes contentLayoutId: Int) : super(contentLayoutId)

    init {
        @Suppress("LeakingThis")
        currentScope.declareActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createViewInner()
        presenter.onViewCreated()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        presenter.onActivityResult(requestCode, resultCode, data)
    }

    protected abstract fun createViewInner()
}