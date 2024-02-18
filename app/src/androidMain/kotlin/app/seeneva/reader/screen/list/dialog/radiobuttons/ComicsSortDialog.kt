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

package app.seeneva.reader.screen.list.dialog.radiobuttons

import android.os.Bundle
import app.seeneva.reader.di.parentFragmentScope
import app.seeneva.reader.logic.entity.query.QuerySort

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