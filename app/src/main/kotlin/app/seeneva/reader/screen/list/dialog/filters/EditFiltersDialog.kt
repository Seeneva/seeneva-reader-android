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

package app.seeneva.reader.screen.list.dialog.filters

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.get
import androidx.core.view.plusAssign
import app.seeneva.reader.R
import app.seeneva.reader.binding.getValue
import app.seeneva.reader.binding.viewBinding
import app.seeneva.reader.databinding.DialogComicFiltersBinding
import app.seeneva.reader.di.autoInit
import app.seeneva.reader.di.getValue
import app.seeneva.reader.di.koinLifecycleScope
import app.seeneva.reader.di.requireParentFragmentScope
import app.seeneva.reader.extension.getSerializableCompat
import app.seeneva.reader.extension.inflate
import app.seeneva.reader.logic.entity.query.filter.Filter
import app.seeneva.reader.logic.entity.query.filter.FilterGroup
import app.seeneva.reader.presenter.PresenterStatefulView
import app.seeneva.reader.screen.list.dialog.BaseDraggableDialog
import org.koin.android.scope.AndroidScopeComponent
import org.koin.core.component.KoinScopeComponent
import java.io.Serializable
import java.util.EnumMap

interface EditFiltersView : PresenterStatefulView {
    fun showFilters(filterGroups: List<FilterGroup>, selectedFilters: Map<FilterGroup.ID, String>)
    fun filtersAccepted(acceptedFilters: Map<FilterGroup.ID, Filter>)
}

class EditFiltersDialog :
    BaseDraggableDialog(R.layout.dialog_comic_filters),
    EditFiltersView,
    KoinScopeComponent,
    AndroidScopeComponent {
    private val viewBinding by viewBinding(DialogComicFiltersBinding::bind)

    private val lifecycleScope = koinLifecycleScope { it.linkTo(requireParentFragmentScope()) }

    override val scope by lifecycleScope

    private val presenter by lifecycleScope.autoInit<EditFiltersPresenter>()

    private val callback by lazy { scope.getOrNull<Callback>() }

    override fun showFilters(
        filterGroups: List<FilterGroup>,
        selectedFilters: Map<FilterGroup.ID, String>
    ) {
        filterGroups.forEach { filterGroup ->
            val filterGroupId = filterGroup.id

            viewBinding.filtersContentView.inflate<View>(
                R.layout.layout_comic_filter,
                true,
                layoutInflater
            )

            val titleView =
                viewBinding.filtersContentView[viewBinding.filtersContentView.childCount - 2] as TextView
            val filtersRadioGroup =
                viewBinding.filtersContentView[viewBinding.filtersContentView.childCount - 1] as RadioGroup

            titleView.text = filterGroup.title

            filterGroup.forEachIndexed { index, filter ->
                filtersRadioGroup += filtersRadioGroup.inflate<RadioButton>(
                    R.layout.layout_radio_item,
                    false,
                    layoutInflater
                ).also {
                    it.id = index
                    it.text = filter.title
                    it.isChecked = when (val selectedFilterId = selectedFilters[filterGroupId]) {
                        null -> filter.none
                        else -> filter.id == selectedFilterId
                    }
                }
            }

            filtersRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                presenter.onFilterSelected(filterGroupId, filterGroup.filters.getOrNull(checkedId))
            }
        }
    }

    override fun filtersAccepted(acceptedFilters: Map<FilterGroup.ID, Filter>) {
        callback?.onFiltersAccepted(acceptedFilters)
        dismiss()
    }

    interface Callback {
        fun onFiltersAccepted(acceptedFilters: Map<FilterGroup.ID, Filter>)
    }

    companion object {
        private const val KEY_SELECTED_FILTERS = "selected_filters"

        fun newInstance(selectedFilters: Map<FilterGroup.ID, Filter>) =
            EditFiltersDialog().apply {
                arguments = Bundle().apply {
                    putSerializable(
                        KEY_SELECTED_FILTERS,
                        selectedFilters.mapValuesTo(EnumMap(FilterGroup.ID::class.java)) { (_, filter) -> filter.id } as Serializable)
                }
            }

        fun readSelectedFilters(dialog: EditFiltersDialog): Map<FilterGroup.ID, String> {
            return requireNotNull(
                dialog.requireArguments()
                    .getSerializableCompat<EnumMap<FilterGroup.ID, String>>(KEY_SELECTED_FILTERS)
            ) { "Use newInstance" }
        }
    }
}