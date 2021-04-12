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

import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.logic.entity.query.filter.FilterGroup
import app.seeneva.reader.logic.entity.query.filter.FilterProvider
import app.seeneva.reader.viewmodel.CoroutineViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface EditFiltersViewModel {
    val filterGroups: StateFlow<List<FilterGroup>>
}

class EditFiltersViewModelImpl(
    dispatchers: Dispatchers,
    filterProvider: FilterProvider
) : CoroutineViewModel(dispatchers), EditFiltersViewModel {
    override val filterGroups = MutableStateFlow<List<FilterGroup>>(emptyList())

    init {
        filterGroups.value = filterProvider.groups()
    }
}