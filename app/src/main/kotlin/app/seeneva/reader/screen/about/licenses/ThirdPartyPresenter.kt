/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
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

package app.seeneva.reader.screen.about.licenses

import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.presenter.BasePresenter
import app.seeneva.reader.presenter.Presenter
import app.seeneva.reader.presenter.PresenterView
import kotlinx.coroutines.flow.StateFlow


interface ThirdPartyPresenter : Presenter {
    val thirdPartyState: StateFlow<ThirdPartyState>
}

class ThirdPartyPresenterImpl(
    view: PresenterView,
    dispatchers: Dispatchers,
    private val viewModel: ThirdPartyViewModel,
) : BasePresenter<PresenterView>(view, dispatchers), ThirdPartyPresenter {
    override val thirdPartyState
        get() = viewModel.thirdPartyState

    init {
        if (thirdPartyState.value === ThirdPartyState.Idle) {
            viewModel.loadThirdParties()
        }
    }
}