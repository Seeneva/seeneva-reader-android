/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
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

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.paging.PagedList
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
import app.seeneva.reader.extension.observe
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
import app.seeneva.reader.screen.list.selection.ComicIdSelectionProvider
import app.seeneva.reader.screen.list.selection.ComicSelectionActionModeObserver
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import org.koin.core.scope.KoinScopeComponent
import org.koin.core.scope.Scope
import org.koin.core.scope.get
import org.koin.core.scope.inject
import java.util.*
import kotlin.math.roundToInt
import kotlin.properties.Delegates

interface ComicsListView : PresenterStatefulView {
    fun showFilters(filters: List<FilterLabel>)

    fun setComicsPagedList(list: PagedList<ComicListItem?>)

    /**
     * Set and show a new screen state
     * @param newScreenState state to show
     */
    fun showScreenState(newScreenState: ScreenState)

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
         * Comic list loading
         */
        STATE_LOADING(false)
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

class ComicsListFragment(_searchView: Lazy<SearchView>) :
    Fragment(R.layout.fragment_comic_list),
    ComicsListView,
    ComicsSortDialog.Callback,
    ComicRenameDialog.Callback,
    EditFiltersDialog.Callback,
    AddModeSelectorDialog.Callback,
    KoinScopeComponent {
    private val viewBinding by viewBinding(FragmentComicListBinding::bind)

    private val searchView by _searchView

    private val lifecycleScope = koinLifecycleScope()

    override val scope: Scope by lifecycleScope

    private val presenter by lifecycleScope.autoInit<ComicsListPresenter>()

    private val router by inject<ComicListRouter>()

    private val gridSpanCount by lazy { resources.getInteger(R.integer.comic_thumb_grid_size) }

    private val allListTypes = ArrayDeque(ComicListViewType.values().asList())

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

    private lateinit var listSelectionTracker: SelectionTracker<Long>

    private val filtersAdapter = FiltersAdapter(object : FiltersAdapter.Callback {
        override fun onFilterClicked(filterLabel: FilterLabel) {
            presenter.removeFilter(filterLabel.groupId)
        }
    })

    private val listAdapter by lazy {
        ComicsAdapter(
            currentListType,
            get(),
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
    private var currentListScreenState: ComicsListView.ScreenState by Delegates.observable(
        ComicsListView.ScreenState.STATE_EMPTY
    ) { _, oldState, newState ->
        if (oldState != newState) {
            //if parent view has scrolling behavior it needs to change nested scrolling
            if (isRootParentScrollingView(requireNotNull(view)) != null) {
                //if list is empty than fling list to show toolbar
                if (oldState == ComicsListView.ScreenState.STATE_DEFAULT) {
                    viewBinding.recyclerView.addOnScrollListener(object :
                        RecyclerView.OnScrollListener() {
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
                    viewBinding.recyclerView.fling(0, -viewBinding.recyclerView.maxFlingVelocity)
                } else {
                    viewBinding.recyclerView.suppressLayout(false)

                    showCurrentState()
                }
            }

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

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
            it.addOnItemTouchListener(PreventBookClickTouchListener { listSelectionTracker.hasSelection() })
        }

        listSelectionTracker = SelectionTracker.Builder(
            COMIC_SELECTION_ID,
            viewBinding.recyclerView,
            ComicIdSelectionProvider(viewBinding.recyclerView),
            ComicDetailsLookup(viewBinding.recyclerView),
            StorageStrategy.createLongStorage()
        ).build().also {
            it.addObserver(
                ComicSelectionActionModeObserver(
                    listAdapter,
                    it,
                    object : ComicSelectionActionModeObserver.ActionModeCallback {
                        override fun start(callback: ActionMode.Callback) =
                            (requireActivity() as AppCompatActivity).startSupportActionMode(callback)

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

            it.onRestoreInstanceState(savedInstanceState)
        }

        router.resultFlow.observe(viewLifecycleOwner) {
            fun showSnackbar(@StringRes msg: Int) {
                Snackbar.make(
                    findSnackBarView(requireView()),
                    msg,
                    Snackbar.LENGTH_SHORT
                ).show()
            }

            when (it) {
                is ComicListRouterResult.ComicBookChose -> {
                    presenter.addComicBooks(it.mode, it.paths, it.flags)
                }
                is ComicListRouterResult.NonExistentBook -> {
                    showSnackbar(R.string.comic_list_error_view_non_existed)
                }
                is ComicListRouterResult.CorruptedComicBook -> {
                    showSnackbar(R.string.comic_list_error_view_corrupted)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean listeners
        searchView.setOnCloseListener(null)
        searchView.setOnQueryTextListener(null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        router.onActivityResult(requestCode, resultCode, data)
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

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
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
            else -> super.onOptionsItemSelected(item)
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
                ViewCompat.animate(viewBinding.filtersRecyclerView)
                    .translationY(targetFilterTranslation.toFloat())
                    .setDuration(170L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .setUpdateListener(object : ViewPropertyAnimatorUpdateListener {
                        private var previousTranslationY =
                            viewBinding.filtersRecyclerView.translationY

                        override fun onAnimationUpdate(view: View) {
                            val translationDiff =
                                (previousTranslationY - view.translationY).roundToInt()

                            viewBinding.recyclerView.updatePadding(top = viewBinding.recyclerView.paddingTop - translationDiff)
                            viewBinding.recyclerView.scrollBy(0, translationDiff)

                            previousTranslationY = view.translationY
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

    override fun setComicsPagedList(list: PagedList<ComicListItem?>) {
        //do not calculate diff on the same list again. It can cause some visual issues
        if (listAdapter.currentList !== list) {
            listAdapter.submitList(list)
        }
    }

    override fun showScreenState(newScreenState: ComicsListView.ScreenState) {
        currentListScreenState = newScreenState
    }

    override fun onComicsMarkedRemoved(ids: Set<Long>) {
        //remove selection if any
        listSelectionTracker.setItemsSelected(ids, false)

        val text =
            resources.getQuantityString(R.plurals.comic_list_comic_book_removed, ids.size, ids.size)

        Snackbar.make(findSnackBarView(requireView()), text, Snackbar.LENGTH_LONG).also {
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
        Snackbar.make(
            findSnackBarView(requireView()),
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

            Snackbar.make(
                findSnackBarView(requireView()),
                R.string.comic_list_error_no_file_manager,
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            AddModeSelectorDialog.newInstance().show(childFragmentManager, TAG_ADD_MODE_SELECTOR)
        } else {
            //we can only import files
            onAddModeSelected(AddComicBookMode.Import)
        }
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
        when (currentListScreenState) {
            ComicsListView.ScreenState.STATE_DEFAULT -> viewBinding.contentMessageView.showContent()
            ComicsListView.ScreenState.STATE_NOTHING_FOUND -> viewBinding.contentMessageView.showMessage(
                R.string.comic_list_message_not_found,
                R.drawable.ic_round_search_24dp
            )
            ComicsListView.ScreenState.STATE_EMPTY -> viewBinding.contentMessageView.showMessage(
                R.string.comic_list_message_empty,
                R.drawable.ic_whale_simple
            )
            ComicsListView.ScreenState.STATE_LOADING -> viewBinding.contentMessageView.showLoading()
        }
    }

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