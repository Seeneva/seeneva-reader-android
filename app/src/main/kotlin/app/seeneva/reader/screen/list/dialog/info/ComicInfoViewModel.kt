/*
 *  This file is part of Seeneva Android Reader
 *  Copyright (C) 2021-2023 Sergei Solodovnikov
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.seeneva.reader.screen.list.dialog.info

import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.logic.entity.ComicInfo
import app.seeneva.reader.logic.usecase.GetComicInfoUseCase
import app.seeneva.reader.viewmodel.CoroutineViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ComicInfoState {
    data object Idle : ComicInfoState
    data class Loading(val id: Long) : ComicInfoState
    data object NotFound : ComicInfoState
    data class Success(val comicInfo: ComicInfo) : ComicInfoState
    data class Error(val t: Throwable) : ComicInfoState
}

interface ComicInfoViewModel {
    val comicInfoState: StateFlow<ComicInfoState>

    fun loadInfo(id: Long)
}

class ComicInfoViewModelImpl(
    dispatchers: Dispatchers,
    private val useCase: GetComicInfoUseCase
) : CoroutineViewModel(dispatchers), ComicInfoViewModel {
    private var comicInfoJob: Job? = null

    private val _comicInfoState = MutableStateFlow<ComicInfoState>(ComicInfoState.Idle)

    override val comicInfoState
        get() = _comicInfoState

    override fun loadInfo(id: Long) {
        when (val currentState = comicInfoState.value) {
            is ComicInfoState.Loading -> {
                if (currentState.id == id) {
                    return
                }
            }

            is ComicInfoState.Success -> {
                if (currentState.comicInfo.id == id) {
                    return
                }
            }

            is ComicInfoState.Error, is ComicInfoState.NotFound, is ComicInfoState.Idle -> {
                //DO_NOTHING
            }
        }

        val previousComicInfoJob = comicInfoJob

        comicInfoJob = vmScope.launch {
            previousComicInfoJob?.cancelAndJoin()

            comicInfoState.value = ComicInfoState.Loading(id)

            comicInfoState.value = runCatching { useCase.byId(id) }
                .map {
                    if (it == null) {
                        ComicInfoState.NotFound
                    } else {
                        ComicInfoState.Success(it)
                    }
                }.getOrElse {
                    if (it is CancellationException) {
                        ComicInfoState.Idle
                    } else {
                        ComicInfoState.Error(it)
                    }
                }
        }
    }
}