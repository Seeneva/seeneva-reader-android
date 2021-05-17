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
import app.seeneva.reader.logic.entity.legal.ThirdParty
import app.seeneva.reader.logic.usecase.ThirdPartyUseCase
import app.seeneva.reader.viewmodel.CoroutineViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

sealed interface ThirdPartyState {
    object Idle : ThirdPartyState
    object Loading : ThirdPartyState
    data class Error(val t: Throwable) : ThirdPartyState
    data class Success(val thirdParties: List<ThirdParty>) : ThirdPartyState
}

interface ThirdPartyViewModel {
    val thirdPartyState: StateFlow<ThirdPartyState>

    /**
     * Start loading third parties dependencies
     */
    fun loadThirdParties()
}

class ThirdPartyViewModelImpl(
    private val useCase: ThirdPartyUseCase,
    dispatchers: Dispatchers
) : CoroutineViewModel(dispatchers), ThirdPartyViewModel {
    private val _thirdPartyState = MutableStateFlow<ThirdPartyState>(ThirdPartyState.Idle)

    override val thirdPartyState = _thirdPartyState.asStateFlow()

    private var thirdPartiesJob: Job? = null

    override fun loadThirdParties() {
        if (thirdPartyState.value == ThirdPartyState.Loading) {
            return
        }

        val currentJob = thirdPartiesJob

        thirdPartiesJob = vmScope.launch {
            currentJob?.cancelAndJoin()

            Logger.info("Start loading third parties")

            _thirdPartyState.value = ThirdPartyState.Loading

            _thirdPartyState.value =
                runCatching { useCase.loadThirdParties() }
                    .map { ThirdPartyState.Success(it) }
                    .recover {
                        if (it !is CancellationException) {
                            ThirdPartyState.Error(it)
                        } else {
                            throw it
                        }
                    }.getOrThrow()
        }
    }
}