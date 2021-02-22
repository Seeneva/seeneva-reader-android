package com.almadevelop.comixreader.screen.list.dialog.info

import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.entity.ComicInfo
import com.almadevelop.comixreader.logic.usecase.GetComicInfoUseCase
import com.almadevelop.comixreader.viewmodel.CoroutineViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ComicInfoState {
    object Idle : ComicInfoState()
    data class Loading(val id: Long) : ComicInfoState()
    object NotFound : ComicInfoState()
    data class Success(val comicInfo: ComicInfo) : ComicInfoState()
    data class Error(val t: Throwable) : ComicInfoState()
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