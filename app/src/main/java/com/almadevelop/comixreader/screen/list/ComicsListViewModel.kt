package com.almadevelop.comixreader.screen.list

import android.net.Uri
import androidx.paging.PagedList
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.ComicsPagingDataSourceFactory
import com.almadevelop.comixreader.logic.ComicsSettings
import com.almadevelop.comixreader.logic.comic.AddComicBookMode
import com.almadevelop.comixreader.logic.comic.Library
import com.almadevelop.comixreader.logic.entity.ComicAddResult
import com.almadevelop.comixreader.logic.entity.ComicListItem
import com.almadevelop.comixreader.logic.entity.query.QueryParams
import com.almadevelop.comixreader.logic.extension.asFlow
import com.almadevelop.comixreader.logic.extension.toPagedList
import com.almadevelop.comixreader.logic.usecase.ComicListUseCase
import com.almadevelop.comixreader.logic.usecase.RenameComicBookUseCase
import com.almadevelop.comixreader.logic.usecase.tags.ComicCompletedTagUseCase
import com.almadevelop.comixreader.logic.usecase.tags.ComicRemovedTagUseCase
import com.almadevelop.comixreader.service.add.AddComicBookServiceConnector
import com.almadevelop.comixreader.viewmodel.CoroutineViewModel
import com.almadevelop.comixreader.viewmodel.EventSender
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

sealed class ListEvents

/**
 * Comic books marked as removed, but not deleted yet
 * @param ids ids of marked comic books
 */
data class ComicsMarkedAsRemoved(val ids: Set<Long>) : ListEvents()

data class ComicsOpened(val result: ComicAddResult) : ListEvents()

sealed class ComicsListState {
    object Idle : ComicsListState()

    object Loading : ComicsListState()

    data class Loaded(
        val list: PagedList<ComicListItem?>,
        val totalCount: Long
    ) : ComicsListState()
}

interface ComicsListViewModel {
    val listState: StateFlow<ComicsListState>

    val eventsFlow: Flow<ListEvents>

    val libraryState: StateFlow<Library.State>

    var queryParams: QueryParams

    /**
     * Start comics list page loading
     * @param pageSize comic book single page size
     * @param initKey init page key
     */
    fun loadList(pageSize: Int, initKey: Int? = null)

    /**
     * @return last used comic book page key
     */
    fun currentPageLastKey(): Int?

    /**
     * Synchronize library
     */
    fun sync()

    /**
     * Add comic book into user library
     * @param path comic book path
     */
    fun add(path: Uri, addComicBookMode: AddComicBookMode, openFlags: Int) {
        add(listOf(path), addComicBookMode, openFlags)
    }

    /**
     * Add comic books into user library
     * @param paths comic books paths
     */
    fun add(paths: List<Uri>, addComicBookMode: AddComicBookMode, openFlags: Int)

    /**
     * Change comic books removed state
     * @param ids comic book ids to set as removed
     * @param removed removed or not
     */
    fun setRemovedState(ids: Set<Long>, removed: Boolean)

    /**
     * Delete comic books
     * @param ids comic book ids to delete
     */
    fun permanentRemove(ids: Set<Long>)

    fun rename(id: Long, title: String)

    fun toggleCompletedMark(id: Long)

    fun setComicsCompletedMark(ids: Set<Long>, completed: Boolean)
}

class ComicsListViewModelImpl(
    dispatchers: Dispatchers,
    private val settings: ComicsSettings,
    private val comicsDataSourceFactory: ComicsPagingDataSourceFactory,
    private val addComicBookServiceConnector: AddComicBookServiceConnector,
    private val comicListUseCase: ComicListUseCase,
    private val removeStateUseCase: ComicRemovedTagUseCase,
    private val renameUseCase: RenameComicBookUseCase,
    private val markComicCompletedUseCase: ComicCompletedTagUseCase,
    private val library: Library,
    job: Job //need to set as parent in the [ComicsPagingDataSourceFactory]
) : CoroutineViewModel(dispatchers, job), ComicsListViewModel {
    override val listState = MutableStateFlow<ComicsListState>(ComicsListState.Idle)

    override val libraryState
        get() = library.state

    override var queryParams by Delegates.observable(
        settings.getComicListQueryParams().also(::onQueryParamsChange)
    ) { _, old, new ->
        if (old != new) {
            vmScope.launch { settings.saveComicListQueryParams(new) }
        }

        onQueryParamsChange(new)
    }

    private val eventsSender = EventSender<ListEvents>()

    override val eventsFlow
        get() = eventsSender.eventState

    private var listLoadJob: ListLoadingJob? = null

    override fun loadList(pageSize: Int, initKey: Int?) {
        listLoadJob?.also {
            val sameRequest = it.isActive && it.pageSize == pageSize && it.initKey == initKey

            if (sameRequest) {
                return
            }
        }

        val prevListLoadJob = listLoadJob

        listLoadJob = ListLoadingJob(pageSize,
            initKey,
            vmScope.launch {
                prevListLoadJob?.cancel()

                listLoadingFlow(pageSize, initKey).collect {
                    val totalCount = comicListUseCase.totalCount(queryParams)

                    if (!it.isDetached) {
                        ensureActive()

                        listState.value =
                            ComicsListState.Loaded(it, totalCount)
                    }
                }
            })
    }

    override fun currentPageLastKey() =
        (listState.value as? ComicsListState.Loaded)?.let { it.list.lastKey as Int }

    override fun sync() {
        //TODO I think it should be moved to a Service...
        vmScope.launch { library.sync() }
    }

    override fun add(paths: List<Uri>, addComicBookMode: AddComicBookMode, openFlags: Int) {
        if (paths.isEmpty()) {
            return
        }

        vmScope.launch {
            eventsSender.sendAll(
                addComicBookServiceConnector.add(
                    paths,
                    addComicBookMode,
                    openFlags
                ).map(::ComicsOpened))
        }
    }

    override fun setRemovedState(ids: Set<Long>, removed: Boolean) {
        vmScope.launch {
            removeStateUseCase.change(ids, removed)

            if (removed) {
                eventsSender.send(ComicsMarkedAsRemoved(ids))
            }
        }
    }

    override fun permanentRemove(ids: Set<Long>) {
        vmScope.launch {
            library.delete(ids)
        }
    }

    override fun rename(id: Long, title: String) {
        vmScope.launch { renameUseCase.byComicBookId(id, title) }
    }

    override fun toggleCompletedMark(id: Long) {
        vmScope.launch { markComicCompletedUseCase.toggle(id) }
    }

    override fun setComicsCompletedMark(ids: Set<Long>, completed: Boolean) {
        vmScope.launch { markComicCompletedUseCase.change(ids, completed) }
    }

    private fun onQueryParamsChange(queryParams: QueryParams) {
        comicsDataSourceFactory.queryParams = queryParams
    }

    /**
     * Create new flow of comic book [PagedList]
     * @param pageSize list page size
     * @param initKey used to determine init loading position
     */
    private fun listLoadingFlow(
        pageSize: Int,
        initKey: Int?
    ) = comicsDataSourceFactory.asFlow()
        .onStart { listState.value = ComicsListState.Loading }
        .toPagedList(
            PagedList.Config
                .Builder()
                .setPageSize(pageSize)
                .build(),
            initKey
        )
        .map {
            //we will have placeholders. So [PagedList] will have null values
            @Suppress("UNCHECKED_CAST")
            it as PagedList<ComicListItem?>
        }
        .onCompletion { listState.value = ComicsListState.Idle }

    private data class ListLoadingJob(
        val pageSize: Int,
        val initKey: Int?,
        private val job: Job
    ) : Job by job
}