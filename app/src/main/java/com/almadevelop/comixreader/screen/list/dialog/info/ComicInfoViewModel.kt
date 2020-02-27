package com.almadevelop.comixreader.screen.list.dialog.info

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.entity.ComicInfo
import com.almadevelop.comixreader.logic.usecase.GetComicInfoUseCase
import com.almadevelop.comixreader.viewmodel.CoroutineViewModel
import kotlinx.coroutines.launch

sealed class ComicInfoState {
    object Loading : ComicInfoState()
    object NotFound : ComicInfoState()
    data class Success(val comicInfo: ComicInfo) : ComicInfoState()
}

interface ComicInfoViewModel {
    val comicInfoState: LiveData<ComicInfoState>

    fun loadInfo(id: Long)
}

class ComicInfoViewModelImpl(
    dispatchers: Dispatchers,
    lazyUseCase: Lazy<GetComicInfoUseCase>
) : CoroutineViewModel(dispatchers), ComicInfoViewModel {
    private val useCase by lazyUseCase

    override val comicInfoState = MutableLiveData<ComicInfoState>()

    override fun loadInfo(id: Long) {
        comicInfoState.value = ComicInfoState.Loading

        launch {
            comicInfoState.value = when (val info = useCase.byId(id)) {
                null -> ComicInfoState.NotFound
                else -> ComicInfoState.Success(info)
            }
        }
    }
}