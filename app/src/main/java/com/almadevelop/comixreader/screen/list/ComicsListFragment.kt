package com.almadevelop.comixreader.screen.list

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.*
import androidx.paging.PagedList
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.di.getOrCreateGlideScope
import com.almadevelop.comixreader.extension.humanDescriptionShort
import com.almadevelop.comixreader.extension.inflate
import com.almadevelop.comixreader.extension.success
import com.almadevelop.comixreader.logic.ComicListViewType
import com.almadevelop.comixreader.logic.comic.AddComicBookMode
import com.almadevelop.comixreader.logic.comic.ComicHelper
import com.almadevelop.comixreader.logic.entity.ComicAddResult
import com.almadevelop.comixreader.logic.entity.ComicListItem
import com.almadevelop.comixreader.logic.entity.query.QuerySort
import com.almadevelop.comixreader.logic.entity.query.filter.Filter
import com.almadevelop.comixreader.logic.entity.query.filter.FilterGroup
import com.almadevelop.comixreader.presenter.BasePresenterFragment
import com.almadevelop.comixreader.presenter.PresenterStatefulView
import com.almadevelop.comixreader.screen.MainContent
import com.almadevelop.comixreader.screen.list.adapter.ComicsAdapter
import com.almadevelop.comixreader.screen.list.adapter.FiltersAdapter
import com.almadevelop.comixreader.screen.list.dialog.AddModeSelectorDialog
import com.almadevelop.comixreader.screen.list.dialog.ComicRenameDialog
import com.almadevelop.comixreader.screen.list.dialog.ComicsSortDialog
import com.almadevelop.comixreader.screen.list.dialog.filters.EditFiltersDialog
import com.almadevelop.comixreader.screen.list.dialog.info.ComicInfoFragment
import com.almadevelop.comixreader.screen.list.entity.FilterLabel
import com.almadevelop.comixreader.screen.list.selection.ComicDetailsLookup
import com.almadevelop.comixreader.screen.list.selection.ComicIdSelectionProvider
import com.almadevelop.comixreader.screen.list.selection.ComicSelectionActionModeObserver
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_comic_list.*
import org.koin.androidx.scope.currentScope
import java.util.*
import kotlin.math.roundToInt
import kotlin.properties.Delegates

interface ComicsListView : PresenterStatefulView {
    /**
     * Show comic book selector
     * @param intent describes selector
     * @param requestCode request code used with startActivityForResult
     */
    fun showComicBookSelector(intent: Intent, requestCode: Int)

    fun showFilters(filters: List<FilterLabel>)

    /**
     * Show all available add modes
     */
    fun showAddModeSelector()

    fun setComicsPagedList(list: PagedList<ComicListItem>, listState: ListState)

    /**
     * Set and show a new screen state
     * @param newScreenState state to show
     */
    fun showScreenState(newScreenState: ScreenState)

    fun updateComicsListState(listState: ListState)

    fun onComicsMarkedRemoved(ids: Set<Long>)

    fun onComicOpened(result: ComicAddResult)

    /**
     * Show sort selector
     * @param currentSort current selected sort
     */
    fun showComicSortTypes(currentSort: QuerySort)

    fun showFiltersEditor(selectedFilters: Map<FilterGroup.ID, Filter>)

    fun setComicListType(listViewType: ComicListViewType)

    fun showNoFileManagerError()

    /**
     * Sync state changed
     * @param state sync state
     */
    fun onSyncStateChanged(state: SyncState)

    /**
     * State of teh whole screen
     * @param menuEnabled is options menu should be visible
     */
    enum class ScreenState(val menuEnabled: Boolean = true) {
        /**
         * Comic books list showed
         */
        STATE_DEFAULT,
        /**
         * Nothing has been found with such filters
         */
        STATE_NOTHING_FOUND,
        /**
         * No comics at all
         */
        STATE_EMPTY(false),
        /**
         * Comic list first time inited
         */
        STATE_INIT(false)
    }

    /**
     * View's sync state
     */
    enum class SyncState {
        /**
         * Sync available and idle
         */
        IDLE,
        /**
         * Sync not available and idle
         */
        DISABLED,
        /**
         * Sync available and in progress
         */
        IN_PROGRESS
    }
}


class ComicsListFragment : BasePresenterFragment(R.layout.fragment_comic_list),
    MainContent,
    ComicsListView,
    ComicsSortDialog.Callback,
    ComicRenameDialog.Callback,
    EditFiltersDialog.Callback,
    AddModeSelectorDialog.Callback {

    private var activityActionBarContent: SearchView? = null

    override val presenter: ComicsListPresenter = currentScope.get()

    private val gridSpanCount by lazy { resources.getInteger(R.integer.comic_thumb_grid_size) }

    private val allListTypes = ArrayDeque(ComicListViewType.values().asList())

    private var currentListType: ComicListViewType by Delegates.observable(ComicListViewType.default) { _, oldType, newType ->
        if (oldType == newType) {
            return@observable
        }

        presenter.onListTypeChanged(newType)

        when (newType) {
            ComicListViewType.Grid -> {
                listLayoutManager.spanCount = gridSpanCount
                listLayoutManager.spanSizeLookup =
                    ComicsGridSpanSizeLookup(listAdapter, gridSpanCount)
            }
            ComicListViewType.List -> {
                listLayoutManager.spanCount = 1
                listLayoutManager.spanSizeLookup = GridLayoutManager.DefaultSpanSizeLookup()
            }
        }

        listAdapter.setComicViewType(newType)

        requireActivity().invalidateOptionsMenu()
    }

    private val listLayoutManager by lazy {
        GridLayoutManager(requireContext(), gridSpanCount).also {
            it.spanSizeLookup = ComicsGridSpanSizeLookup(listAdapter, gridSpanCount)
        }
    }

    private lateinit var listSelectionTracker: SelectionTracker<Long>

    private val filtersAdapter = FiltersAdapter(object : FiltersAdapter.Callback {
        override fun onFilterClicked(filterLabel: FilterLabel) {
            presenter.removeFilter(filterLabel.groupId)
        }
    })

    private val listAdapter = ComicsAdapter(
        currentListType,
        object : ComicsAdapter.Callback {
            override fun onItemDeleteClick(comic: ComicListItem) {
                presenter.deleteComicBook(Collections.singleton(comic.id))
            }

            override fun onItemRenameClick(comic: ComicListItem) {
                showRenameComicBookWindow(comic)
            }

            override fun onItemInfoClick(comic: ComicListItem) {
                if (childFragmentManager.findFragmentByTag(TAG_INFO) == null) {
                    ComicInfoFragment.newInstance(comic.id, comic.title.toString())
                        .show(childFragmentManager, TAG_INFO)
                }
            }

            override fun onMarkAsReadClick(comic: ComicListItem) {
                presenter.toggleComicCompletedMark(comic.id)
            }

            override fun isItemSelected(comic: ComicListItem): Boolean {
                return listSelectionTracker.isSelected(comic.id)
            }
        })


    /**
     * ScreenState of the list
     */
    private var currentListScreenState: ComicsListView.ScreenState by Delegates.observable(
        ComicsListView.ScreenState.STATE_EMPTY
    ) { _, oldState, newState ->
        if (oldState != newState) {
            //if parent view has scrolling behavior it needs to change nested scrolling
            if (isRootParentScrollingView(requireNotNull(view)) != null) {
                //if list is empty than fling list to show toolbar
                if (oldState == ComicsListView.ScreenState.STATE_DEFAULT) {
                    recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(
                            recyclerView: RecyclerView,
                            newState: Int
                        ) {
                            super.onScrollStateChanged(recyclerView, newState)
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                recyclerView.suppressLayout(true)
                                recyclerView.removeOnScrollListener(this)

                                showCurrentState()
                            }
                        }
                    })

                    //fling to restore appbarLayout visibility
                    recyclerView.fling(0, -recyclerView.maxFlingVelocity)
                } else {
                    recyclerView.suppressLayout(false)

                    showCurrentState()
                }
            }

            activityActionBarContent?.also { activityActionBarContent ->
                fun setViewEnabled(v: View, enabled: Boolean) {
                    if (v is ViewGroup) {
                        v.forEach {
                            setViewEnabled(it, enabled)
                        }
                    }
                    v.isEnabled = enabled
                }

                //need to change enabled state of all children
                setViewEnabled(activityActionBarContent, newState.menuEnabled)
            }

            requireActivity().invalidateOptionsMenu()
        }
    }

    /**
     * Current sync state
     */
    private var currentSyncState by Delegates.observable(ComicsListView.SyncState.IDLE) { _, _, newState ->
        with(swipeSyncView) {
            when (newState) {
                ComicsListView.SyncState.IN_PROGRESS -> {
                    isEnabled = true
                    isRefreshing = true
                }
                ComicsListView.SyncState.IDLE -> {
                    isEnabled = true
                    isRefreshing = false
                }
                ComicsListView.SyncState.DISABLED -> {
                    isEnabled = false
                    isRefreshing = false
                }
            }
        }

        //need to update options menu
        requireActivity().invalidateOptionsMenu()
    }

    init {
        //prepare glide scope to be able use it on attached views
        getOrCreateGlideScope()
    }

    override fun prepareView(savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)

        with(swipeSyncView) {
            setColorSchemeResources(R.color.deep_purple_400)
            setOnRefreshListener { presenter.onSyncClick() }
        }

        filtersRecyclerView.also {
            it.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            it.adapter = filtersAdapter

            it.doOnNextLayout { v ->
                v.translationY = -v.height.toFloat()
            }
        }

        recyclerView.also {
            it.setHasFixedSize(true)

            it.layoutManager = listLayoutManager
            it.adapter = listAdapter

            it.addItemDecoration(ComicGridMarginDecoration(resources.getDimensionPixelSize(R.dimen.comic_thumb_grid_margin)))
            it.addOnItemTouchListener(PreventBookClickTouchListener { listSelectionTracker.hasSelection() })
        }

        listSelectionTracker = SelectionTracker.Builder(
            COMIC_SELECTION_ID,
            recyclerView,
            ComicIdSelectionProvider(recyclerView),
            ComicDetailsLookup(recyclerView),
            StorageStrategy.createLongStorage()
        ).build().also {
            it.addObserver(
                ComicSelectionActionModeObserver(
                    requireActivity() as AppCompatActivity,
                    listAdapter,
                    it,
                    object : ComicSelectionActionModeObserver.ActionModeCallback {
                        override fun onMarkAsRemovedSelectedClick() {
                            presenter.deleteComicBook(listSelectionTracker.selection.toHashSet())
                        }

                        override fun onMarkAsCompletedSelectedClick(completed: Boolean) {
                            presenter.setComicsCompletedMark(
                                listSelectionTracker.selection.toHashSet(),
                                completed
                            )
                        }
                    })
            )

            it.onRestoreInstanceState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        listSelectionTracker.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.comics_list, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        if (currentListScreenState.menuEnabled) {
            with(menu.findItem(R.id.list_view)) {
                val iconResId: Int
                val titleResId: Int

                when (currentListType) {
                    ComicListViewType.Grid -> {
                        iconResId = R.drawable.ic_round_view_list_24dp
                        titleResId = R.string.comic_list_as_list
                    }
                    ComicListViewType.List -> {
                        iconResId = R.drawable.ic_round_view_module_24dp
                        titleResId = R.string.comic_list_as_grid
                    }
                }

                icon = AppCompatResources.getDrawable(requireContext(), iconResId)
                setTitle(titleResId)
            }

            with(menu.findItem(R.id.sync)) {
                isEnabled = currentSyncState == ComicsListView.SyncState.IDLE
            }
        } else {
            menu.forEach {
                it.isVisible = it.itemId == R.id.add
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.add -> {
                presenter.onAddComicBookClick()
                true
            }
            R.id.sort -> {
                presenter.onSortListClick()
                true
            }
            R.id.list_view -> {
                nextComicListType()
                true
            }
            R.id.filter -> {
                presenter.onEditFilterClick()
                true
            }
            R.id.sync -> {
                presenter.onSyncClick()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun activityActionBarContent(parent: ViewGroup): View? {
        return if (activityActionBarContent != null) {
            activityActionBarContent
        } else {
            parent.inflate<SearchView>(R.layout.layout_main_search).also {
                it.isSubmitButtonEnabled = false
                it.isFocusable = false
                it.isIconified = false

                it.clearFocus()

                it.setOnCloseListener { true }

                it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        return true
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        presenter.onSearchQuery(newText)
                        return true
                    }
                })
            }
        }
    }

    override fun showComicBookSelector(intent: Intent, requestCode: Int) {
        startActivityForResult(intent, requestCode)
    }

    override fun showFilters(filters: List<FilterLabel>) {
        if (filters.isNotEmpty()) {
            filtersAdapter.submitList(filters)
        }

        val animateFilters: (Boolean) -> Unit = { filterShowing: Boolean ->
            //target translationY of the filter panel
            val targetFilterTranslation: Int
            //precomputated final padding value of the comic book list
            val resultComicListTopPadding: Int

            if (filterShowing) {
                targetFilterTranslation = 0
                resultComicListTopPadding = recyclerView.paddingTop + filtersRecyclerView.height
            } else {
                targetFilterTranslation = -filtersRecyclerView.height
                resultComicListTopPadding = recyclerView.paddingTop - filtersRecyclerView.height
            }

            if (filtersRecyclerView.translationY != targetFilterTranslation.toFloat()) {
                ViewCompat.animate(filtersRecyclerView)
                    .translationY(targetFilterTranslation.toFloat())
                    .setDuration(170L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .setUpdateListener(object : ViewPropertyAnimatorUpdateListener {
                        private var previousTranslationY = filtersRecyclerView.translationY

                        override fun onAnimationUpdate(view: View) {
                            val translationDiff =
                                (previousTranslationY - view.translationY).roundToInt()

                            recyclerView.updatePadding(top = recyclerView.paddingTop - translationDiff)
                            recyclerView.scrollBy(0, translationDiff)

                            previousTranslationY = view.translationY
                        }
                    })
                    .also {
                        if (filterShowing) {
                            it.withStartAction { filtersRecyclerView.isVisible = true }
                        }

                        it.withEndAction {
                            if (!filterShowing) {
                                filtersRecyclerView.isInvisible = true
                            }

                            //just fix different between float value (translationY) and Int value (padding)
                            recyclerView.updatePadding(top = resultComicListTopPadding)
                        }
                    }
            }
        }

        filtersRecyclerView.doOnLayout { animateFilters(filters.isNotEmpty()) }

    }

    override fun showAddModeSelector() {
        AddModeSelectorDialog.newInstance().show(childFragmentManager, TAG_ADD_MODE_SELECTOR)
    }

    override fun setComicsPagedList(list: PagedList<ComicListItem>, listState: ListState) {
        listAdapter.submitList(list, listState)
    }

    override fun updateComicsListState(listState: ListState) {
        listAdapter.updateListState(listState)

        //it will prevent from list scrolling after some user's changes (e.g rename or completed state change)
        if(recyclerView.computeVerticalScrollOffset() > 0) {
            recyclerView.scrollBy(0, 0)
        }
    }

    override fun showScreenState(newScreenState: ComicsListView.ScreenState) {
        currentListScreenState = newScreenState
    }

    override fun onComicsMarkedRemoved(ids: Set<Long>) {
        val text =
            resources.getQuantityString(R.plurals.comic_list_comic_book_removed, ids.size, ids.size)

        Snackbar.make(findSnackBarView(view!!), text, Snackbar.LENGTH_LONG).also {
            it.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
                    super.onDismissed(transientBottomBar, event)

                    if (event != DISMISS_EVENT_ACTION) {
                        presenter.deleteComicBook(ids, true)
                    }
                }
            })

            it.setAction(R.string.comic_list_undo_delete) { presenter.undoComicRemove(ids) }
        }.show()
    }

    override fun onComicOpened(result: ComicAddResult) {
        Snackbar.make(
            findSnackBarView(view!!), result.humanDescriptionShort(resources), if (result.success) {
                Snackbar.LENGTH_SHORT
            } else {
                Snackbar.LENGTH_LONG
            }
        ).show()
    }

    override fun showComicSortTypes(currentSort: QuerySort) {
        if (childFragmentManager.findFragmentByTag(TAG_SORT) == null) {
            ComicsSortDialog.newInstance(currentSort.key).show(childFragmentManager, TAG_SORT)
        }
    }

    override fun showFiltersEditor(selectedFilters: Map<FilterGroup.ID, Filter>) {
        if (childFragmentManager.findFragmentByTag(TAG_EDIT_FILTERS) == null) {
            EditFiltersDialog.newInstance(selectedFilters)
                .show(childFragmentManager, TAG_EDIT_FILTERS)
        }
    }

    override fun onSortChecked(dialog: ComicsSortDialog, sort: QuerySort) {
        dialog.dismiss()

        presenter.onSortSelected(sort)
    }

    override fun onFiltersAccepted(acceptedFilters: Map<FilterGroup.ID, Filter>) {
        presenter.onFiltersAccepted(acceptedFilters)
    }

    override fun onAddModeSelecetd(selectedMode: AddComicBookMode) {
        presenter.onAddModeSelected(selectedMode)
    }

    override fun onTitleRenamed(id: Long, newTitle: String) {
        presenter.renameComicBook(id, newTitle)
    }

    override fun setComicListType(listViewType: ComicListViewType) {
        if (allListTypes.remove(listViewType)) {
            allListTypes.addFirst(listViewType)

            currentListType = listViewType
        }
    }

    override fun showNoFileManagerError() {
        val installFileManagerIntent = ComicHelper.installFileManagerIntent

        Snackbar.make(
            findSnackBarView(view!!),
            R.string.comic_list_error_no_file_manager,
            Snackbar.LENGTH_SHORT
        ).also {
            if (installFileManagerIntent.resolveActivity(requireContext().packageManager) != null) {
                it.setAction(R.string.search) { startActivity(installFileManagerIntent) }
            }
        }.show()
    }

    override fun onSyncStateChanged(state: ComicsListView.SyncState) {
        currentSyncState = state
    }

    private fun nextComicListType() {
        allListTypes.remove().also { allListTypes.addLast(it) }

        currentListType = allListTypes.first
    }

    /**
     * Show comic book rename window
     * @param comic comic book toi rename
     */
    private fun showRenameComicBookWindow(comic: ComicListItem) {
        if (childFragmentManager.findFragmentByTag(TAG_RENAME) == null) {
            ComicRenameDialog.newInstance(comic).show(childFragmentManager, TAG_RENAME)
        }
    }

    /**
     * Show current list state
     */
    private fun showCurrentState() {
        when (currentListScreenState) {
            ComicsListView.ScreenState.STATE_DEFAULT -> contentMessageView.showContent()
            ComicsListView.ScreenState.STATE_NOTHING_FOUND -> contentMessageView.showMessage(
                R.string.comic_list_message_not_found,
                R.drawable.ic_round_search_24dp
            )
            ComicsListView.ScreenState.STATE_EMPTY -> contentMessageView.showMessage(
                R.string.comic_list_message_empty,
                R.drawable.ic_whale_simple
            )
            ComicsListView.ScreenState.STATE_INIT -> contentMessageView.showLoading()
        }
    }

    private class ComicsGridSpanSizeLookup(
        private val adapter: ComicsAdapter,
        private val spanCount: Int
    ) : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            return if (adapter.isLoaderPosition(position)) {
                spanCount
            } else {
                1
            }
        }
    }

    /**
     * Set margin between comic items in the grid
     * @param margin margin between items
     */
    private class ComicGridMarginDecoration(private val margin: Int) :
        RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            outRect.offset(margin, margin)
        }
    }

    private class PreventBookClickTouchListener(
        private val hasSelection: () -> Boolean
    ) : RecyclerView.SimpleOnItemTouchListener() {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            if (hasSelection()) {
                if (e.action == MotionEvent.ACTION_DOWN) {
                    val childView = rv.findChildViewUnder(e.x, e.y)

                    if (childView != null) {
                        val vh = rv.getChildViewHolder(childView) as? ComicsAdapter.ComicsViewHolder

                        return vh?.getSelectionDetails(e) == null
                    }
                }
            }

            return false
        }
    }

    private companion object {
        private const val COMIC_SELECTION_ID = "comic_path_selection"

        private const val TAG_SORT = "sort"
        private const val TAG_RENAME = "rename"
        private const val TAG_INFO = "info"
        private const val TAG_EDIT_FILTERS = "edit_filters"
        private const val TAG_ADD_MODE_SELECTOR = "add_mode_selector"

        /**
         * Trying to find the better place to a [Snackbar]
         * @param root current fragment root view
         */
        fun findSnackBarView(root: View): View {
            //current snackbar revision will try to find first [CoordinatorLayout] from bottom to top
            //we need it to prevent showing a Snackbar outside of the screen when using AppBarLayout.ScrollingViewBehavior
            return when (val parentScrollingView = isRootParentScrollingView(root)) {
                null -> root
                else -> parentScrollingView
            }
        }

        /**
         * @return not null if root's View parent has [AppBarLayout.ScrollingViewBehavior]
         */
        fun isRootParentScrollingView(root: View): View? {
            val parentView = root.parent as? View

            val isHasScrollingBehavior =
                (parentView?.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior is AppBarLayout.ScrollingViewBehavior

            return if (isHasScrollingBehavior) {
                parentView
            } else {
                null
            }
        }
    }
}