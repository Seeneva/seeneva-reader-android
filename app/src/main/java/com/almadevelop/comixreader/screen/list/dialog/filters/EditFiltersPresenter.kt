package com.almadevelop.comixreader.screen.list.dialog.filters

import android.os.Bundle
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.entity.query.filter.Filter
import com.almadevelop.comixreader.logic.entity.query.filter.FilterGroup
import com.almadevelop.comixreader.presenter.BaseStatefulPresenter
import com.almadevelop.comixreader.presenter.ComponentPresenter

interface EditFiltersPresenter : ComponentPresenter {
    fun onFilterSelected(groupId: FilterGroup.ID, filter: Filter?)
}

class EditFiltersPresenterImpl(
    view: EditFiltersView,
    dispatchers: Dispatchers,
    lazyInitSelectedFilters: Lazy<Map<FilterGroup.ID, String>>,
    lazyViewModel: Lazy<EditFiltersViewModel>
) : BaseStatefulPresenter<EditFiltersView>(view, dispatchers), EditFiltersPresenter {
    private val viewModel by lazyViewModel
    private val initSelectedFilters by lazyInitSelectedFilters

    private val selectedFilters = hashMapOf<FilterGroup.ID, String>()

    override fun onCreate(state: Bundle?) {
        if (state == null) {
            selectedFilters.putAll(initSelectedFilters)
        } else {
            (state.getSerializable(STATE_SELECTED_FILTERS) as Map<FilterGroup.ID, String>).also {
                selectedFilters.putAll(it)
            }
        }
    }

    override fun saveState(): Bundle {
        return Bundle().apply {
            putSerializable(STATE_SELECTED_FILTERS, selectedFilters)
        }
    }

    override fun onViewCreated() {
        super.onViewCreated()

        viewModel.filterGroups.observe {
            view.showFilters(it, selectedFilters)
        }
    }

    override fun onFilterSelected(groupId: FilterGroup.ID, filter: Filter?) {
        if (filter != null) {
            selectedFilters[groupId] = filter.id
        } else {
            selectedFilters.remove(groupId)
        }

        //it is temporary cause it has only one filter group for now
        acceptFilters()
    }

    private fun acceptFilters() {
        view.filtersAccepted(HashMap<FilterGroup.ID, Filter>(selectedFilters.size).also { acceptedFilters ->
            requireNotNull(viewModel.filterGroups.value).forEach { filterGroup ->
                val selectedFilterId = selectedFilters[filterGroup.id]

                if (selectedFilterId != null) {
                    acceptedFilters[filterGroup.id] =
                        filterGroup.first { it.id == selectedFilterId }
                }
            }
        })
    }

    companion object {
        private const val STATE_SELECTED_FILTERS = "selected_filters"
    }
}