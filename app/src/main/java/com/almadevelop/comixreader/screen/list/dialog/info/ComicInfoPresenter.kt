package com.almadevelop.comixreader.screen.list.dialog.info

import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.presenter.BasePresenter
import com.almadevelop.comixreader.presenter.ComponentPresenter

interface ComicInfoPresenter : ComponentPresenter {
    fun loadComicBookInfo(id: Long)
}

class ComicInfoPresenterImpl(
    view: ComicInfoView,
    dispatchers: Dispatchers,
    lazyViewModel: Lazy<ComicInfoViewModel>
) : BasePresenter<ComicInfoView>(view, dispatchers), ComicInfoPresenter {
    private val viewModel by lazyViewModel

    override fun onViewCreated() {
        super.onViewCreated()

        viewModel.comicInfoState.observe {
            view.showComicInfoState(it)
        }
    }

    override fun loadComicBookInfo(id: Long) {
        //load only if the LiveData has no value
        if (viewModel.comicInfoState.value == null) {
            viewModel.loadInfo(id)
        }
    }
}