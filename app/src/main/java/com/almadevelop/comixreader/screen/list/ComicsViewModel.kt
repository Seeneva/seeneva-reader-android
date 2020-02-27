package com.almadevelop.comixreader.screen.list

import android.net.Uri
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.paging.DataSource
import androidx.paging.PagedList
import androidx.paging.toLiveData
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.extension.asLiveData
import com.almadevelop.comixreader.logic.ComicsPagingDataSourceFactory
import com.almadevelop.comixreader.logic.ComicsSettings
import com.almadevelop.comixreader.logic.comic.AddComicBookMode
import com.almadevelop.comixreader.logic.comic.Library
import com.almadevelop.comixreader.logic.entity.ComicListItem
import com.almadevelop.comixreader.logic.entity.ComicAddResult
import com.almadevelop.comixreader.logic.entity.query.QueryParams
import com.almadevelop.comixreader.logic.usecase.ComicListUseCase
import com.almadevelop.comixreader.logic.usecase.tags.ComicRemovedTagUseCase
import com.almadevelop.comixreader.logic.usecase.tags.ComicCompletedTagUseCase
import com.almadevelop.comixreader.logic.usecase.RenameComicBookUseCase
import com.almadevelop.comixreader.service.add.AddComicBookServiceConnector
import com.almadevelop.comixreader.viewmodel.CoroutineViewModel
import com.almadevelop.comixreader.viewmodel.Event
import com.almadevelop.comixreader.viewmodel.intoEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*

sealed class ListEvents
/**
 * Comic books marked as removed, but not deleted yet
 * @param ids ids of marked comic books
 */
data class ComicsMarkedAsRemoved(val ids: Set<Long>) : ListEvents()

data class ComicsOpened(val result: ComicAddResult) : ListEvents()

sealed class ComicsLoadingState {
    object Init : ComicsLoadingState()

    data class Loaded(
        val list: PagedList<ComicListItem>,
        val totalCount: Long,
        val listStateLiveData: LiveData<ListState>
    ) : ComicsLoadingState() {
        val currentListState: ListState
            get() = requireNotNull(listStateLiveData.value) { "List state should be set" }
    }
}

data class ListState(
    val frontReached: Boolean = false,
    val endReached: Boolean = false
)

interface ComicsViewModel {
    val listLoadingStateLiveData: LiveData<ComicsLoadingState>

    val eventsLiveData: LiveData<Event<ListEvents>>

    val libraryStateLiveData: LiveData<Library.State>

    var queryParams: QueryParams

    /**
     * Init comic page loader
     * @param pageSize comic book single page size
     * @param initKey init page key
     */
    fun initComicsListLoading(pageSize: Int, initKey: Int? = null)

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
        add(Collections.singletonList(path), addComicBookMode, openFlags)
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

class ComicsViewModelImpl(
    dispatchers: Dispatchers,
    private val settings: ComicsSettings,
    private val comicsDataSourceFactory: ComicsPagingDataSourceFactory,
    private val lazyAddComicBookServiceConnector: Lazy<AddComicBookServiceConnector>,
    lazyComicLisUseCase: Lazy<ComicListUseCase>,
    lazyRemovedStateUseCase: Lazy<ComicRemovedTagUseCase>,
    lazyRenameUseCase: Lazy<RenameComicBookUseCase>,
    lazyMarkComicCompletedUseCase: Lazy<ComicCompletedTagUseCase>,
    lazyLibrary: Lazy<Library>,
    job: Job //need to set as parent in the [ComicsPagingDataSourceFactory]
) : CoroutineViewModel(dispatchers, job), ComicsViewModel {
    override val listLoadingStateLiveData = MutableLiveData<ComicsLoadingState>()
        .also { it.value = ComicsLoadingState.Init }

    override val eventsLiveData = MutableLiveData<Event<ListEvents>>()

    override val libraryStateLiveData by lazy { library.stateFlow().asLiveData(dispatchers) }

    override var queryParams: QueryParams = initQueryParams()
        set(value) {
            if (field != value) {
                settings.saveComicListQueryParams(value)
            }

            field = value
            onQueryParamsChange(value)
        }

    private var listStateLiveData: MutableLiveData<ListState>

    private val currentListState: ListState
        get() = requireNotNull(listStateLiveData.value) { "List state should be initialized" }

    private val addComicBookServiceConnector: AddComicBookServiceConnector
        get() {
            if (!lazyAddComicBookServiceConnector.isInitialized()) {

                launch {
                    val resultsChannel = lazyAddComicBookServiceConnector.value.subscribe()

                    resultsChannel.collect {
                        eventsLiveData.value = ComicsOpened(it).intoEvent()
                    }
                }
            }

            return lazyAddComicBookServiceConnector.value
        }

    private val comicListUseCase by lazyComicLisUseCase
    private val removeStateUseCase by lazyRemovedStateUseCase
    private val renameUseCase by lazyRenameUseCase
    private val markComicCompletedUseCase by lazyMarkComicCompletedUseCase

    private val library by lazyLibrary

    private var comicListInit = false

    init {
        val newListStateLiveData = { MutableLiveData<ListState>().also { it.value = ListState() } }

        listStateLiveData = newListStateLiveData()

        comicsDataSourceFactory.invalidateCallback = DataSource.InvalidatedCallback {
            //It can be called from any thread. So sync it
            if (Looper.getMainLooper().thread === Thread.currentThread()) {
                listStateLiveData = newListStateLiveData()
            } else {
                runBlocking(coroutineContext) { listStateLiveData = newListStateLiveData() }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (lazyAddComicBookServiceConnector.isInitialized()) {
            addComicBookServiceConnector.close()
        }
    }

    override fun initComicsListLoading(pageSize: Int, initKey: Int?) {
        if (comicListInit) {
            return
        }

        comicListInit = true

        val comicsBoundaryCallback = object : PagedList.BoundaryCallback<ComicListItem>() {
            override fun onItemAtEndLoaded(itemAtEnd: ComicListItem) {
                super.onItemAtEndLoaded(itemAtEnd)
                sendListState(currentListState.copy(endReached = true))
            }

            override fun onItemAtFrontLoaded(itemAtFront: ComicListItem) {
                super.onItemAtFrontLoaded(itemAtFront)
                sendListState(currentListState.copy(frontReached = true))
            }

            private fun sendListState(newListState: ListState) {
                if (currentListState != newListState) {
                    listStateLiveData.value = newListState
                }
            }
        }

        comicsDataSourceFactory.toLiveData(
            PagedList.Config
                .Builder()
                .setEnablePlaceholders(false)
                .setPageSize(pageSize)
                .build(),
            initKey,
            comicsBoundaryCallback
        ).observeForever { list ->
            launch {
                val totalCount = comicListUseCase.totalCount(queryParams)

                ensureActive()

                if (!list.isDetached) {
                    listLoadingStateLiveData.value =
                        ComicsLoadingState.Loaded(list, totalCount, listStateLiveData)
                }
            }
        }
    }

    override fun currentPageLastKey(): Int? {
        return (listLoadingStateLiveData.value as? ComicsLoadingState.Loaded)?.list?.lastKey as Int?
    }

    override fun sync() {
        launch { library.sync() }
    }

    override fun add(paths: List<Uri>, addComicBookMode: AddComicBookMode, openFlags: Int) {
        if (paths.isEmpty()) {
            return
        }

        launch {
            addComicBookServiceConnector.add(paths, addComicBookMode, openFlags)
        }
    }

    override fun setRemovedState(ids: Set<Long>, removed: Boolean) {
        launch {
            removeStateUseCase.change(ids, removed)

            if (removed) {
                eventsLiveData.value = ComicsMarkedAsRemoved(ids).intoEvent()
            }
        }
    }

    override fun permanentRemove(ids: Set<Long>) {
        launch {
            library.delete(ids)
        }
    }

    override fun rename(id: Long, title: String) {
        launch { renameUseCase.byComicBookId(id, title) }
    }

    override fun toggleCompletedMark(id: Long) {
        launch { markComicCompletedUseCase.toggle(id) }
    }

    override fun setComicsCompletedMark(ids: Set<Long>, completed: Boolean) {
        launch { markComicCompletedUseCase.change(ids, completed) }
    }

    private fun initQueryParams(): QueryParams {
        return settings.getComicListQueryParams().also(::onQueryParamsChange)
    }

    private fun onQueryParamsChange(queryParams: QueryParams) {
        comicsDataSourceFactory.queryParams = queryParams
    }
}