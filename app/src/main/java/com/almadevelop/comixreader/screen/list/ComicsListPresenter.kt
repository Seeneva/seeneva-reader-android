package com.almadevelop.comixreader.screen.list

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.ComicListViewType
import com.almadevelop.comixreader.logic.ComicsSettings
import com.almadevelop.comixreader.logic.comic.AddComicBookMode
import com.almadevelop.comixreader.logic.comic.ComicHelper
import com.almadevelop.comixreader.logic.comic.Library
import com.almadevelop.comixreader.logic.entity.query.QueryParams
import com.almadevelop.comixreader.logic.entity.query.QuerySort
import com.almadevelop.comixreader.logic.entity.query.filter.Filter
import com.almadevelop.comixreader.logic.entity.query.filter.FilterGroup
import com.almadevelop.comixreader.presenter.BaseStatefulPresenter
import com.almadevelop.comixreader.presenter.ComponentPresenter
import com.almadevelop.comixreader.screen.list.entity.FilterLabel
import org.tinylog.kotlin.Logger
import com.almadevelop.comixreader.screen.list.ComicsListView.ScreenState as ViewScreenState

interface ComicsListPresenter : ComponentPresenter {
    val currentSearchQuery: String?

    /**
     * User click on add comic book button
     */
    fun onAddComicBookClick()

    /**
     * User click on edit list filters button
     */
    fun onEditFilterClick()

    /**
     * User chose an add mode for a comic book
     */
    fun onAddModeSelected(addComicBookMode: AddComicBookMode)

    /**
     * User click on sync button
     */
    fun onSyncClick()

    /**
     * Delete or mark as deleted
     * @param ids ids of comic book
     * @param permanent true - delete, false - mark as removed
     */
    fun deleteComicBook(ids: Set<Long>, permanent: Boolean = false)

    /**
     * Undo mark as removed
     * @param ids ids of comic book
     */
    fun undoComicRemove(ids: Set<Long>)

    /**
     * On sort comic book list button click
     */
    fun onSortListClick()

    /**
     * User selected a buildNew sort
     * @param selectedSort buildNew sort type
     */
    fun onSortSelected(selectedSort: QuerySort)

    fun onFiltersAccepted(acceptedFilters: Map<FilterGroup.ID, Filter>)

    fun removeFilter(groupId: FilterGroup.ID)

    /**
     * Rename comic book
     * @param id ikd of the comic book to rename
     * @param title buildNew title of the comic book
     */
    fun renameComicBook(id: Long, title: String)

    fun onSearchQuery(query: String?)

    fun toggleComicCompletedMark(id: Long)

    /**
     * Set completed mark to all comics with provided [ids]
     * @param ids comic books ids
     * @param completed which flag should be set
     */
    fun setComicsCompletedMark(ids: Set<Long>, completed: Boolean)

    fun onListTypeChanged(listType: ComicListViewType)
}

class ComicsListPresenterImpl(
    view: ComicsListView,
    dispatchers: Dispatchers,
    private val settings: ComicsSettings,
    lazyViewModel: Lazy<ComicsViewModel>
) : BaseStatefulPresenter<ComicsListView>(view, dispatchers), ComicsListPresenter {
    private val viewModel by lazyViewModel

    private var queryParams: QueryParams
        get() = viewModel.queryParams
        set(newQueryParams) {
            if (queryParams.filters != newQueryParams.filters) {
                showFilters(newQueryParams)
            }

            viewModel.queryParams = newQueryParams
        }

    override val currentSearchQuery: String?
        get() = queryParams.titleQuery

    override fun onCreate(state: Bundle?) {
        view.setComicListType(settings.getComicListType())

        var comicPageInitKey = 0

        if (state != null) {
            //restore titleQuery if needed
            onSearchQuery(state.getString(STATE_SEARCH_QUERY))

            comicPageInitKey = state.getInt(STATE_PAGE_LAST_KEY, 0)
        }

        viewModel.initComicsListLoading(COMIC_PAGE_SIZE, comicPageInitKey)
    }

    override fun onViewCreated() {
        super.onViewCreated()

        viewModel.listLoadingStateLiveData.observe { comicsLoadingState ->
            when (comicsLoadingState) {
                is ComicsLoadingState.Init -> {
                    view.showScreenState(ViewScreenState.STATE_INIT)
                }
                is ComicsLoadingState.Loaded -> {
                    val (list, totalCount, listStateLiveData) = comicsLoadingState

                    listStateLiveData.observe { view.updateComicsListState(it) }

                    view.setComicsPagedList(list, comicsLoadingState.currentListState)

                    view.showScreenState(
                        when {
                            totalCount == 0L -> ViewScreenState.STATE_EMPTY
                            list.isEmpty() && totalCount > 0 -> ViewScreenState.STATE_NOTHING_FOUND
                            else -> ViewScreenState.STATE_DEFAULT
                        }
                    )
                }
            }
        }

        viewModel.eventsLiveData.observe {
            if (!it.handled) {
                it.handled = true

                when (val content = it.content) {
                    is ComicsMarkedAsRemoved -> view.onComicsMarkedRemoved(content.ids)
                    is ComicsOpened -> view.onComicOpened(content.result)
                }
            }
        }

        viewModel.libraryStateLiveData.observe {
            when (it) {
                Library.State.IDLE -> ComicsListView.SyncState.IDLE
                Library.State.SYNCING -> ComicsListView.SyncState.IN_PROGRESS
                Library.State.CHANGING -> ComicsListView.SyncState.DISABLED
            }.also { state ->
                Logger.debug("Set view sync state to: $state")

                view.onSyncStateChanged(state)
            }
        }

        showFilters(queryParams)
    }

    override fun saveState(): Bundle {
        val outState = Bundle()

        outState.putString(STATE_SEARCH_QUERY, currentSearchQuery)

        viewModel.currentPageLastKey()?.also {
            outState.putInt(STATE_PAGE_LAST_KEY, it)
        }

        return outState
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val openMode = openModeFromRequestCode(requestCode)

        if (openMode != null) {
            if (resultCode == Activity.RESULT_OK) {
                requireNotNull(data) { "Comic books opening result doesn't have any data" }

                val dataContent = data.data
                val dataClipData = data.clipData

                when {
                    dataContent != null -> viewModel.add(dataContent, openMode, data.flags)
                    dataClipData != null -> {
                        val paths =
                            (0 until dataClipData.itemCount).map { dataClipData.getItemAt(it).uri }
                        viewModel.add(paths, openMode, data.flags)
                    }
                    else -> throw Error("Result intent doesn't have any data $data")
                }
            }
        }
    }

    override fun onAddComicBookClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.showAddModeSelector()
        } else {
            //we can only import files
            onAddModeSelected(AddComicBookMode.Import)
        }
    }

    override fun onEditFilterClick() {
        view.showFiltersEditor(queryParams.filters)
    }

    override fun onAddModeSelected(addComicBookMode: AddComicBookMode) {
        val openComicBookIntent = ComicHelper.openComicBookIntent(addComicBookMode)

        //sync is any activity can resolve that Intent
        if (openComicBookIntent.resolveActivity(context.packageManager) != null) {
            view.showComicBookSelector(
                openComicBookIntent,
                openRequestCode(addComicBookMode)
            )
        } else {
            view.showNoFileManagerError()
        }
    }

    override fun onSyncClick() {
        viewModel.sync()
    }

    override fun deleteComicBook(ids: Set<Long>, permanent: Boolean) {
        if (permanent) {
            viewModel.permanentRemove(ids)
        } else {
            viewModel.setRemovedState(ids, true)
        }
    }

    override fun undoComicRemove(ids: Set<Long>) {
        viewModel.setRemovedState(ids, false)
    }

    override fun onSortListClick() {
        view.showComicSortTypes(queryParams.sort)
    }

    override fun onSortSelected(selectedSort: QuerySort) {
        if (queryParams.sort == selectedSort) {
            return
        }

        queryParams = queryParams.buildNew { sort = selectedSort }
    }

    override fun onFiltersAccepted(acceptedFilters: Map<FilterGroup.ID, Filter>) {
        queryParams = queryParams.buildNew {
            acceptedFilters.forEach { (id, filter) -> addFilter(id, filter) }
        }
    }

    override fun removeFilter(groupId: FilterGroup.ID) {
        if (!queryParams.filters.containsKey(groupId)) {
            return
        }

        queryParams = queryParams.buildNew { removeFilter(groupId) }
    }

    override fun renameComicBook(id: Long, title: String) {
        viewModel.rename(id, title)
    }

    override fun onSearchQuery(query: String?) {
        if (queryParams.titleQuery == query) {
            return
        }

        queryParams = queryParams.buildNew { titleQuery = query }
    }

    override fun toggleComicCompletedMark(id: Long) {
        viewModel.toggleCompletedMark(id)
    }

    override fun setComicsCompletedMark(ids: Set<Long>, completed: Boolean) {
        viewModel.setComicsCompletedMark(ids, completed)
    }

    override fun onListTypeChanged(listType: ComicListViewType) {
        settings.saveComicListType(listType)
    }

    private fun showFilters(queryParams: QueryParams) {
        view.showFilters(queryParams.filters.map { (groupId, filter) ->
            FilterLabel(groupId, filter.title)
        })
    }

    private companion object {
        private const val COMIC_PAGE_SIZE = 15

        private const val REQUEST_ADD_COMIC_BOOK = 100

        private const val STATE_SEARCH_QUERY = "search_query"
        private const val STATE_PAGE_LAST_KEY = "page_last_key"

        private fun openRequestCode(addComicBookMode: AddComicBookMode) =
            REQUEST_ADD_COMIC_BOOK or (addComicBookMode.ordinal + 1)

        private fun openModeFromRequestCode(requestCode: Int): AddComicBookMode? {
            AddComicBookMode.values().forEach {
                val id = it.ordinal + 1

                if (requestCode and id == id) {
                    return it
                }
            }

            return null
        }
    }
}