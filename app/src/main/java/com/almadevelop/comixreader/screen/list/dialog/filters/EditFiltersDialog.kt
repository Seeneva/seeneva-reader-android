package com.almadevelop.comixreader.screen.list.dialog.filters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.get
import androidx.core.view.plusAssign
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.binding.getValue
import com.almadevelop.comixreader.binding.viewBinding
import com.almadevelop.comixreader.databinding.DialogComicFiltersBinding
import com.almadevelop.comixreader.di.autoInit
import com.almadevelop.comixreader.di.getValue
import com.almadevelop.comixreader.di.koinLifecycleScope
import com.almadevelop.comixreader.di.requireParentFragmentScope
import com.almadevelop.comixreader.extension.inflate
import com.almadevelop.comixreader.logic.entity.query.filter.Filter
import com.almadevelop.comixreader.logic.entity.query.filter.FilterGroup
import com.almadevelop.comixreader.presenter.PresenterStatefulView
import com.almadevelop.comixreader.screen.list.dialog.BaseDraggableDialog
import org.koin.core.scope.KoinScopeComponent
import java.io.Serializable
import java.util.*

interface EditFiltersView : PresenterStatefulView {
    fun showFilters(filterGroups: List<FilterGroup>, selectedFilters: Map<FilterGroup.ID, String>)
    fun filtersAccepted(acceptedFilters: Map<FilterGroup.ID, Filter>)
}

class EditFiltersDialog : BaseDraggableDialog(), EditFiltersView, KoinScopeComponent {
    private val viewBinding by viewBinding(DialogComicFiltersBinding::bind)

    private val lifecycleScope = koinLifecycleScope { it.linkTo(requireParentFragmentScope()) }

    override val scope by lifecycleScope

    private val presenter by lifecycleScope.autoInit<EditFiltersPresenter>()

    private val callback by lazy { scope.getOrNull<Callback>() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_comic_filters, container, false)
    }

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
            val args = requireNotNull(dialog.arguments) { "Use newInstance" }

            return args.getSerializable(KEY_SELECTED_FILTERS) as EnumMap<FilterGroup.ID, String>
        }
    }
}