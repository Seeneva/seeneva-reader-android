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

package app.seeneva.reader.screen.list.dialog.filters

import android.os.Bundle
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.extension.observe
import app.seeneva.reader.logic.entity.query.filter.Filter
import app.seeneva.reader.logic.entity.query.filter.FilterGroup
import app.seeneva.reader.presenter.BaseStatefulPresenter
import app.seeneva.reader.presenter.Presenter

interface EditFiltersPresenter : Presenter {
    fun onFilterSelected(groupId: FilterGroup.ID, filter: Filter?)
}

class EditFiltersPresenterImpl(
    view: EditFiltersView,
    dispatchers: Dispatchers,
    private val initSelectedFilters: Map<FilterGroup.ID, String>,
    private val viewModel: EditFiltersViewModel
) : BaseStatefulPresenter<EditFiltersView>(view, dispatchers), EditFiltersPresenter {
    private val selectedFilters = hashMapOf<FilterGroup.ID, String>()

    init {
        viewModel.filterGroups.observe(view) {
            view.showFilters(it, selectedFilters)
        }
    }

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