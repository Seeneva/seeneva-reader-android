/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
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

package app.seeneva.reader.screen.list

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import androidx.paging.LoadState
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.seeneva.reader.R
import app.seeneva.reader.binding.getValue
import app.seeneva.reader.binding.viewBinding
import app.seeneva.reader.databinding.FragmentComicListBinding
import app.seeneva.reader.di.autoInit
import app.seeneva.reader.di.getValue
import app.seeneva.reader.di.koinLifecycleScope
import app.seeneva.reader.extension.humanDescriptionShort
import app.seeneva.reader.extension.success
import app.seeneva.reader.logic.ComicListViewType
import app.seeneva.reader.logic.comic.AddComicBookMode
import app.seeneva.reader.logic.comic.ComicHelper
import app.seeneva.reader.logic.entity.ComicAddResult
import app.seeneva.reader.logic.entity.ComicListItem
import app.seeneva.reader.logic.entity.query.QuerySort
import app.seeneva.reader.logic.entity.query.filter.Filter
import app.seeneva.reader.logic.entity.query.filter.FilterGroup
import app.seeneva.reader.presenter.PresenterStatefulView
import app.seeneva.reader.screen.list.adapter.ComicsAdapter
import app.seeneva.reader.screen.list.adapter.FiltersAdapter
import app.seeneva.reader.screen.list.dialog.AddModeSelectorDialog
import app.seeneva.reader.screen.list.dialog.ComicRenameDialog
import app.seeneva.reader.screen.list.dialog.filters.EditFiltersDialog
import app.seeneva.reader.screen.list.dialog.info.ComicInfoFragment
import app.seeneva.reader.screen.list.dialog.radiobuttons.ComicsSortDialog
import app.seeneva.reader.screen.list.entity.FilterLabel
import app.seeneva.reader.screen.list.selection.ComicDetailsLookup
import app.seeneva.reader.screen.list.selection.ComicKeyProvider
import app.seeneva.reader.screen.list.selection.ComicSelectionActionModeObserver
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.scope.AndroidScopeComponent
import org.koin.core.component.KoinScopeComponent
import org.koin.core.scope.Scope
import java.util.ArrayDeque
import kotlin.math.roundToInt
import kotlin.properties.Delegates

/**
 * State of teh whole screen
 * @param menuEnabled is options menu should be visible
 */
private enum class ScreenState(val menuEnabled: Boolean = true) {
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
     * Comic list loading
     */
    STATE_LOADING(false)
}

interface ComicsListView : PresenterStatefulView {
    fun showFilters(filters: List<FilterLabel>)

    fun onComicsMarkedRemoved(ids: Set<Long>)

    fun onComicAdded(result: ComicAddResult)

    /**
     * Show sort selector
     * @param currentSort current selected sort
     */
    fun showComicSortTypes(currentSort: QuerySort)

    fun showFiltersEditor(selectedFilters: Map<FilterGroup.ID, Filter>)

    fun setComicListType(listViewType: ComicListViewType)

    /**
     * Sync state changed
     * @param state sync state
     */
    fun onSyncStateChanged(state: SyncState)

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

/**
 * @param _searchView inflated search view
 * @param expandAppBar called to expand [AppBarLayout] if any
 */
class ComicsListFragment(
    _searchView: Lazy<SearchView>,
    private val expandAppBar: () -> Unit = {}
) : Fragment(R.layout.fragment_comic_list),
    ComicsListView,
    ComicsSortDialog.Callback,
    ComicRenameDialog.Callback,
    EditFiltersDialog.Callback,
    AddModeSelectorDialog.Callback,
    KoinScopeComponent,
    AndroidScopeComponent {
    private val viewBinding by viewBinding(FragmentComicListBinding::bind)

    private val searchView by _searchView

    private val lifecycleScope = koinLifecycleScope()

    override val scope: Scope by lifecycleScope

    private val presenter by lifecycleScope.autoInit<ComicsListPresenter>()

    private val router by lifecycleScope.autoInit<ComicListRouter>()

    private val gridSpanCount by lazy { resources.getInteger(R.integer.comic_thumb_grid_size) }

    private val allListTypes = ArrayDeque(ComicListViewType.entries)

    private var currentListType: ComicListViewType by Delegates.observable(ComicListViewType.default) { _, oldType, newType ->
        if (oldType == newType) {
            return@observable
        }

        presenter.onListTypeChanged(newType)

        when (newType) {
            ComicListViewType.GRID -> {
                listLayoutManager.spanCount = gridSpanCount
            }

            ComicListViewType.LIST -> {
                listLayoutManager.spanCount = 1
            }
        }

        listAdapter.setComicViewType(newType)

        requireActivity().invalidateOptionsMenu()
    }

    private val listLayoutManager by lazy { GridLayoutManager(requireContext(), gridSpanCount) }

    private val listSelectionTracker: SelectionTracker<Long> by lazy {
        SelectionTracker.Builder(
            COMIC_SELECTION_ID,
            viewBinding.recyclerView,
            ComicKeyProvider(listAdapter),
            ComicDetailsLookup(viewBinding.recyclerView),
            StorageStrategy.createLongStorage()
        ).withSelectionPredicate(SelectionPredicates.createSelectAnything())
            .build()
            .also {
                it.addObserver(
                    ComicSelectionActionModeObserver(
                        listAdapter,
                        it,
                        viewLifecycleOwner.lifecycle,
                        object : ComicSelectionActionModeObserver.ActionModeCallback {
                            override fun start(callback: ActionMode.Callback) =
                                (requireActivity() as AppCompatActivity).startSupportActionMode(
                                    callback
                                )

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

                it.addObserver(object : SelectionTracker.SelectionObserver<Long>() {
                    override fun onSelectionChanged() {
                        super.onSelectionChanged()
                        //prevent swipe to scroll event while selectiong
                        viewBinding.swipeSyncView.isEnabled = !listSelectionTracker.hasSelection()
                    }
                })
            }
    }

    private val filtersAdapter = FiltersAdapter(object : FiltersAdapter.Callback {
        override fun onFilterClicked(filterLabel: FilterLabel) {
            presenter.removeFilter(filterLabel.groupId)
        }
    })

    private val listAdapter by lazy {
        ComicsAdapter(
            currentListType,
            get(),
            layoutInflater,
            object : ComicsAdapter.Callback {
                override fun onItemDeleteClick(comic: ComicListItem) {
                    presenter.deleteComicBook(setOf(comic.id))
                }

                override fun onItemRenameClick(comic: ComicListItem) {
                    showRenameComicBook(comic)
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

                override fun onComicBookClick(comic: ComicListItem) {
                    //open comic book viewer only if where is no selection
                    if (!listSelectionTracker.hasSelection()) {
                        router.showComicBookViewer(comic.id)
                    }
                }
            })
    }

    /**
     * ScreenState of the list
     */
    private val currentListScreenState = MutableStateFlow(ScreenState.STATE_EMPTY)

    /**
     * Current sync state
     */
    private var currentSyncState by Delegates.observable(ComicsListView.SyncState.IDLE) { _, _, newState ->
        with(viewBinding.swipeSyncView) {
            when (newState) {
                ComicsListView.SyncState.IN_PROGRESS -> {
                    isEnabled = !listSelectionTracker.hasSelection()
                    isRefreshing = true
                }

                ComicsListView.SyncState.IDLE -> {
                    isEnabled = !listSelectionTracker.hasSelection()
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

    private val notificationPermissionCallback = object : ActivityResultCallback<Boolean> {
        // describes comic book to add
        var addComicBookData: ComicListRouterResult.AddComicBooks? = null

        override fun onActivityResult(result: Boolean) {
            val data = addComicBookData ?: return

            addComicBook(data)

            addComicBookData = null
        }

        fun addComicBook(addComicBookData: ComicListRouterResult.AddComicBooks) {
            newSnackbar(resources.getString(R.string.comic_list_message_add_progress)).show()

            presenter.addComicBooks(
                addComicBookData.mode,
                addComicBookData.result.paths,
                addComicBookData.result.permissionFlags
            )
        }
    }

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.comics_list, menu)
        }

        override fun onPrepareMenu(menu: Menu) {
            super.onPrepareMenu(menu)

            if (currentListScreenState.value.menuEnabled) {
                with(menu.findItem(R.id.list_view)) {
                    val iconResId: Int
                    val titleResId: Int

                    when (currentListType) {
                        ComicListViewType.GRID -> {
                            iconResId = R.drawable.ic_round_view_list_24dp
                            titleResId = R.string.comic_list_as_list
                        }

                        ComicListViewType.LIST -> {
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

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
            when (menuItem.itemId) {
                R.id.add -> {
                    onAddComicBookClick()
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

                else ->
                    false
            }
    }

    /**
     * Launch notification permission request dialog
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        notificationPermissionCallback
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        searchView.setOnCloseListener { true }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                presenter.onSearchQuery(newText)
                return true
            }
        })

        with(viewBinding.swipeSyncView) {
            setColorSchemeResources(R.color.deep_purple_400)
            setOnRefreshListener { presenter.onSyncClick() }
        }

        viewBinding.filtersRecyclerView.also {
            it.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

            it.addItemDecoration(FilterDecoration(resources.getDimensionPixelSize(R.dimen.comic_filter_list_margin)))

            it.adapter = filtersAdapter

            it.doOnNextLayout { v ->
                v.translationY = -v.height.toFloat()
            }
        }

        viewBinding.recyclerView.also {
            it.setHasFixedSize(true)

            it.layoutManager = listLayoutManager
            it.adapter = listAdapter

            it.addItemDecoration(ComicGridMarginDecoration(resources.getDimensionPixelSize(R.dimen.comic_thumb_grid_margin)))
        }

        // should be called after setting an adapter to the recyclerview
        listSelectionTracker.onRestoreInstanceState(savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // listen to screen state change
                    currentListScreenState.collect { newState ->
                        when (newState) {
                            ScreenState.STATE_DEFAULT -> {
                                viewBinding.recyclerView.suppressLayout(false)
                            }

                            else -> {
                                // disable scrolling and force show AppBarLayout
                                viewBinding.recyclerView.suppressLayout(true)
                                expandAppBar()
                            }
                        }

                        showCurrentState()

                        searchView.also { searchView ->
                            fun setViewEnabled(v: View, enabled: Boolean) {
                                if (v is ViewGroup) {
                                    v.forEach {
                                        setViewEnabled(it, enabled)
                                    }
                                }
                                v.isEnabled = enabled
                            }

                            //need to change enabled state of all children
                            setViewEnabled(searchView, newState.menuEnabled)
                        }

                        requireActivity().invalidateOptionsMenu()
                    }
                }

                launch {
                    // Here we set every loaded paging data to the Adapter
                    presenter.pagingState
                        .filterIsInstance<ComicsPagingState.Loaded>()
                        .collect { listAdapter.submitData(it.pagingData) }
                }

                launch {
                    // listen to pagination states and update screen state
                    presenter.pagingState
                        .transformLatest {
                            when (it) {
                                ComicsPagingState.Loading, ComicsPagingState.Idle -> emit(
                                    ScreenState.STATE_LOADING
                                )

                                is ComicsPagingState.Loaded -> {
                                    when (it.totalCount) {
                                        0L -> emit(ScreenState.STATE_EMPTY)
                                        else -> {
                                            emitAll(listAdapter.loadStateFlow
                                                .filter { s ->
                                                    s.refresh is LoadState.NotLoading &&
                                                            s.prepend is LoadState.NotLoading &&
                                                            s.append is LoadState.NotLoading
                                                }
                                                .map { s ->
                                                    if (listAdapter.itemCount == 0 && s.prepend.endOfPaginationReached && s.append.endOfPaginationReached) {
                                                        ScreenState.STATE_NOTHING_FOUND
                                                    } else {
                                                        ScreenState.STATE_DEFAULT
                                                    }
                                                })
                                        }
                                    }
                                }
                            }
                        }.collect { currentListScreenState.value = it }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            router.resultFlow.collect {
                viewLifecycleOwner.withStarted {
                    when (it) {
                        is ComicListRouterResult.AddComicBooks -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                                    requireContext(),
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionCallback.addComicBookData = it
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                notificationPermissionCallback.addComicBook(it)
                            }
                        }

                        is ComicListRouterResult.NonExistentBook -> {
                            newSnackbar(resources.getString(R.string.comic_list_error_view_non_existed))
                        }

                        is ComicListRouterResult.CorruptedComicBook -> {
                            newSnackbar(resources.getString(R.string.comic_list_error_view_corrupted))
                        }
                    }
                }
            }
        }

        // restore last visible item and start loading list paging
        presenter.loadComicsPagingData(savedInstanceState?.getInt(STATE_LIST_FIRST_ITEM) ?: 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean listeners
        searchView.setOnCloseListener(null)
        searchView.setOnQueryTextListener(null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        listSelectionTracker.onSaveInstanceState(outState)
        outState.putInt(STATE_LIST_FIRST_ITEM, listLayoutManager.findFirstVisibleItemPosition())
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
                resultComicListTopPadding =
                    viewBinding.recyclerView.paddingTop + viewBinding.filtersRecyclerView.height
            } else {
                targetFilterTranslation = -viewBinding.filtersRecyclerView.height
                resultComicListTopPadding =
                    viewBinding.recyclerView.paddingTop - viewBinding.filtersRecyclerView.height
            }

            if (viewBinding.filtersRecyclerView.translationY != targetFilterTranslation.toFloat()) {
                viewBinding.filtersRecyclerView.animate()
                    .translationY(targetFilterTranslation.toFloat())
                    .setDuration(170L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .setUpdateListener(object : AnimatorUpdateListener {
                        private var previousTranslationY =
                            viewBinding.filtersRecyclerView.translationY

                        override fun onAnimationUpdate(animation: ValueAnimator) {
                            val newTranslation = animation.animatedValue as Float

                            val translationDiff =
                                (previousTranslationY - newTranslation).roundToInt()

                            viewBinding.recyclerView.updatePadding(top = viewBinding.recyclerView.paddingTop - translationDiff)
                            viewBinding.recyclerView.scrollBy(0, translationDiff)

                            previousTranslationY = newTranslation
                        }
                    })
                    .also {
                        if (filterShowing) {
                            it.withStartAction { viewBinding.filtersRecyclerView.isVisible = true }
                        }

                        it.withEndAction {
                            if (!filterShowing) {
                                viewBinding.filtersRecyclerView.isInvisible = true
                            }

                            //just fix different between float value (translationY) and Int value (padding)
                            viewBinding.recyclerView.updatePadding(top = resultComicListTopPadding)
                        }
                    }
            }
        }

        viewBinding.filtersRecyclerView.doOnLayout { animateFilters(filters.isNotEmpty()) }
    }

    override fun onComicsMarkedRemoved(ids: Set<Long>) {
        //remove selection if any
        listSelectionTracker.setItemsSelected(ids, false)

        val text =
            resources.getQuantityString(R.plurals.comic_list_comic_book_removed, ids.size, ids.size)

        newSnackbar(text, Snackbar.LENGTH_LONG).also {
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

    override fun onComicAdded(result: ComicAddResult) {
        newSnackbar(
            result.humanDescriptionShort(resources),
            if (result.success) {
                Snackbar.LENGTH_SHORT
            } else {
                Snackbar.LENGTH_LONG
            }
        ).show()
    }

    override fun showComicSortTypes(currentSort: QuerySort) {
        if (childFragmentManager.findFragmentByTag(TAG_SORT) == null) {
            ComicsSortDialog.newInstance(currentSort).show(childFragmentManager, TAG_SORT)
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

    override fun onAddModeSelected(selectedMode: AddComicBookMode) {
        if (!router.showComicBookSelector(selectedMode)) {
            //show install package manager from store message
            val installFileManagerIntent = ComicHelper.installFileManagerIntent

            newSnackbar(
                resources.getString(R.string.comic_list_error_no_file_manager),
                Snackbar.LENGTH_SHORT
            ).also {
                if (installFileManagerIntent.resolveActivity(requireContext().packageManager) != null) {
                    it.setAction(R.string.search) { startActivity(installFileManagerIntent) }
                }
            }.show()
        }
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

    override fun onSyncStateChanged(state: ComicsListView.SyncState) {
        currentSyncState = state
    }

    private fun onAddComicBookClick() {
        AddModeSelectorDialog.newInstance().show(childFragmentManager, TAG_ADD_MODE_SELECTOR)
    }

    private fun nextComicListType() {
        allListTypes.remove().also { allListTypes.addLast(it) }

        currentListType = allListTypes.first
    }

    /**
     * Show comic book rename window
     * @param comic comic book toi rename
     */
    private fun showRenameComicBook(comic: ComicListItem) {
        if (childFragmentManager.findFragmentByTag(TAG_RENAME) == null) {
            ComicRenameDialog.newInstance(comic).show(childFragmentManager, TAG_RENAME)
        }
    }

    /**
     * Show current list state
     */
    private fun showCurrentState() {
        when (currentListScreenState.value) {
            ScreenState.STATE_DEFAULT -> viewBinding.contentMessageView.showContent()
            ScreenState.STATE_NOTHING_FOUND -> viewBinding.contentMessageView.showMessage(
                R.string.comic_list_message_not_found,
                R.drawable.ic_round_search_24dp
            )

            ScreenState.STATE_EMPTY -> viewBinding.contentMessageView.showMessage(
                R.string.comic_list_message_empty,
                R.drawable.ic_whale_simple
            )

            ScreenState.STATE_LOADING -> viewBinding.contentMessageView.showLoading()
        }
    }

    private fun newSnackbar(
        msg: String,
        duration: Int = Snackbar.LENGTH_SHORT,
        view: View = findSnackBarView(requireView()),
    ) = Snackbar.make(view, msg, duration)

    /**
     * Filter list decorator which add margin between items
     */
    private class FilterDecoration(private val margin: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)

            val halfMargin = margin / 2

            outRect.set(
                if (position == 0) margin else halfMargin,
                outRect.top,
                if (position == parent.adapter?.itemCount?.minus(1)) margin else halfMargin,
                outRect.bottom
            )
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

    private companion object {
        private const val STATE_LIST_FIRST_ITEM = "comic_list_first_item"

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