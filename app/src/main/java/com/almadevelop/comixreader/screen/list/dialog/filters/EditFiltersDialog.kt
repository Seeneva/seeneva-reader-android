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
import com.almadevelop.comixreader.extension.inflate
import com.almadevelop.comixreader.extension.parentKoinScope
import com.almadevelop.comixreader.extension.setDraggableBackground
import com.almadevelop.comixreader.logic.entity.query.filter.Filter
import com.almadevelop.comixreader.logic.entity.query.filter.FilterGroup
import com.almadevelop.comixreader.presenter.BasePresenterBottomSheetDialog
import com.almadevelop.comixreader.presenter.PresenterStatefulView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.android.synthetic.main.dialog_comic_filters.*
import org.koin.androidx.scope.currentScope
import java.io.Serializable
import java.util.*

interface EditFiltersView : PresenterStatefulView {
    fun showFilters(filterGroups: List<FilterGroup>, selectedFilters: Map<FilterGroup.ID, String>)
    fun filtersAccepted(acceptedFilters: Map<FilterGroup.ID, Filter>)
}

class EditFiltersDialog : BasePresenterBottomSheetDialog(), EditFiltersView {
    init {
        setStyle(STYLE_NORMAL, R.style.AppBottomSheetTheme_NonCollapsed)
    }

    override val presenter = currentScope.get<EditFiltersPresenter>()

    private val callback by lazy { parentKoinScope.getOrNull<Callback>() }

    private val bottomSheetBehavior by lazy {
        requireNotNull(dialog).findViewById<View>(R.id.design_bottom_sheet)
            .let { BottomSheetBehavior.from(it) }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setDraggableBackground()

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

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

            filtersContentView.inflate<View>(R.layout.layout_comic_filter, true, layoutInflater)

            val titleView = filtersContentView[filtersContentView.childCount - 2] as TextView
            val filtersRadioGroup =
                filtersContentView[filtersContentView.childCount - 1] as RadioGroup

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