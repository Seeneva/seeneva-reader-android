package com.almadevelop.comixreader.screen.list.dialog.info

import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.presenter.BasePresenter
import com.almadevelop.comixreader.presenter.Presenter
import kotlinx.coroutines.flow.StateFlow

interface ComicInfoPresenter : Presenter {
    val comicInfoState: StateFlow<ComicInfoState>
}

class ComicInfoPresenterImpl(
    view: ComicInfoView,
    dispatchers: Dispatchers,
    private val viewModel: ComicInfoViewModel,
    bookId: Long
) : BasePresenter<ComicInfoView>(view, dispatchers), ComicInfoPresenter {
    override val comicInfoState
        get() = viewModel.comicInfoState

    init {
        viewModel.loadInfo(bookId)
    }
}