package com.almadevelop.comixreader.screen.list.dialog.radiobuttons

import android.os.Bundle
import com.almadevelop.comixreader.di.parentFragmentScope
import com.almadevelop.comixreader.logic.entity.query.QuerySort

class ComicsSortDialog : RadioButtonDialog<String, QuerySort>() {
    override val values = QuerySort.all()

    private val callback by lazy { parentFragmentScope?.getOrNull<Callback>() }

    override fun putKey(bundle: Bundle, key: String) {
        bundle.putSort(key)
    }

    override fun getKey(bundle: Bundle) =
        requireNotNull(bundle.getSort()) { "Can't get selected sort key" }

    override fun valueKey(value: QuerySort) = value.key

    override fun valueTitle(value: QuerySort) = requireContext().getString(value.titleResId)

    override fun onValueCheck(value: QuerySort) {
        callback?.onSortChecked(this, value)
    }

    interface Callback {
        /**
         * Comic book sort was checked
         * @param dialog
         * @param sort checked sort value
         */
        fun onSortChecked(dialog: ComicsSortDialog, sort: QuerySort)
    }

    companion object {
        private const val KEY_CURRENT_SORT = "sort_key"

        fun newInstance(selectedSort: QuerySort) =
            ComicsSortDialog()
                .apply {
                    arguments = Bundle().apply { putSort(selectedSort.key) }
                }

        private fun Bundle.putSort(selectedSortKey: String) {
            putString(KEY_CURRENT_SORT, selectedSortKey)
        }

        private fun Bundle.getSort(): String? = getString(KEY_CURRENT_SORT)
    }
}