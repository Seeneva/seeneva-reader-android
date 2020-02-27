package com.almadevelop.comixreader.screen.list.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.view.plusAssign
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.extension.inflate
import com.almadevelop.comixreader.extension.parentKoinScope
import com.almadevelop.comixreader.extension.setDraggableBackground
import com.almadevelop.comixreader.logic.entity.query.QuerySort
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.dialog_comic_list_sort.*

class ComicsSortDialog : BottomSheetDialogFragment() {
    private val allSort by lazy { QuerySort.all() }

    private lateinit var selectedSortKey: String

    private val callback by lazy<Callback?> { parentKoinScope.getOrNull() }

    private val bottomSheetBehavior by lazy {
        requireNotNull(dialog).findViewById<View>(R.id.design_bottom_sheet)
            .let { BottomSheetBehavior.from(it) }
    }

    init {
        setStyle(STYLE_NORMAL, R.style.AppBottomSheetTheme_NonCollapsed)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedSortKey = if (savedInstanceState != null) {
            requireNotNull(savedInstanceState.getSort()) { "Cannot restore selected sort key" }
        } else {
            requireNotNull(arguments?.getSort()) { "Cannot find sort key in the arguments" }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_comic_list_sort, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setDraggableBackground()

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        allSort.forEachIndexed { i, sort ->
            sortGroup += (sortGroup.inflate<RadioButton>(
                R.layout.layout_radio_item,
                false,
                layoutInflater
            )).also {
                it.id = i
                it.isChecked = sort.key == selectedSortKey
                it.setText(sort.titleResId)
            }
        }

        sortGroup.setOnCheckedChangeListener { _, checkedId ->
            callback?.onSortChecked(this, allSort[checkedId])
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putSort(selectedSortKey)
    }

    interface Callback {
        fun onSortChecked(dialog: ComicsSortDialog, sort: QuerySort)
    }

    companion object {
        private const val KEY_CURRENT_SORT = "sort_key"

        fun newInstance(selectedSortKey: String) =
            ComicsSortDialog().apply {
                arguments = Bundle().apply { putSort(selectedSortKey) }
            }

        private fun Bundle.putSort(selectedSortKey: String) {
            putString(KEY_CURRENT_SORT, selectedSortKey)
        }

        private fun Bundle.getSort(): String? = getString(KEY_CURRENT_SORT)
    }
}