package com.almadevelop.comixreader.extension

import androidx.fragment.app.Fragment
import org.koin.androidx.scope.currentScope
import org.koin.core.scope.Scope

/**
 * Return Koin scope from parent Fragment or Activity
 */
val Fragment.parentKoinScope: Scope
    get() = parentFragment?.currentScope ?: requireActivity().currentScope

