/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.seeneva.reader.presenter

import android.os.Bundle
import androidx.savedstate.SavedStateRegistry
import app.seeneva.reader.common.coroutines.Dispatched
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.extension.registerAndRestore
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
        registerAndRestore(view, restore = ::onCreate)
    }

    protected abstract fun onCreate(state: Bundle?)
}