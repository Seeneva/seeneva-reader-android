package com.almadevelop.comixreader.screen.list.dialog.filters

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.entity.query.filter.FilterGroup
import com.almadevelop.comixreader.logic.entity.query.filter.FilterProvider
import com.almadevelop.comixreader.viewmodel.CoroutineViewModel

interface EditFiltersViewModel {
    val filterGroups: LiveData<List<FilterGroup>>
}

class EditFiltersViewModelImpl(
    dispatchers: Dispatchers,
    filterProvider: FilterProvider
) : CoroutineViewModel(dispatchers), EditFiltersViewModel {
    override val filterGroups = MutableLiveData<List<FilterGroup>>()

    init {
        filterGroups.value = filterProvider.groups()
    }
}