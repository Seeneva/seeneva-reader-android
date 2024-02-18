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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.view.plusAssign
import app.seeneva.reader.R
import app.seeneva.reader.binding.getValue
import app.seeneva.reader.binding.viewBinding
import app.seeneva.reader.databinding.DialogComicListRadioButtonsBinding
import app.seeneva.reader.extension.inflate
import app.seeneva.reader.screen.list.dialog.BaseDraggableDialog

abstract class RadioButtonDialog<K : Any, V : Any> : BaseDraggableDialog() {
    private val viewBinding by viewBinding(DialogComicListRadioButtonsBinding::bind)

    protected abstract val values: Array<V>

    private lateinit var selectedKey: K

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedKey = if (savedInstanceState != null) {
            getKey(savedInstanceState)
        } else {
            getKey(requireArguments())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_comic_list_radio_buttons, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        values.forEachIndexed { i, value ->
            viewBinding.radioGroup += (viewBinding.radioGroup.inflate<RadioButton>(
                R.layout.layout_radio_item,
                false,
                layoutInflater
            )).also {
                it.id = i
                it.isChecked = valueKey(value) == selectedKey
                it.text = valueTitle(value)
            }
        }

        viewBinding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedValue = values[checkedId]

            selectedKey = valueKey(selectedValue)

            onValueCheck(selectedValue)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        putKey(outState, selectedKey)
    }

    protected abstract fun putKey(bundle: Bundle, key: K)

    protected abstract fun getKey(bundle: Bundle): K

    protected abstract fun valueKey(value: V): K

    protected abstract fun valueTitle(value: V): CharSequence

    protected abstract fun onValueCheck(value: V)
}