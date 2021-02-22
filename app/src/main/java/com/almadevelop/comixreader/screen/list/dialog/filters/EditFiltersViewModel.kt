package com.almadevelop.comixreader.screen.list.dialog.filters

import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.entity.query.filter.FilterGroup
import com.almadevelop.comixreader.logic.entity.query.filter.FilterProvider
import com.almadevelop.comixreader.viewmodel.CoroutineViewModel
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