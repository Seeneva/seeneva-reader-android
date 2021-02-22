package com.almadevelop.comixreader.screen.list.dialog.radiobuttons

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.view.plusAssign
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.binding.getValue
import com.almadevelop.comixreader.binding.viewBinding
import com.almadevelop.comixreader.databinding.DialogComicListRadioButtonsBinding
import com.almadevelop.comixreader.extension.inflate
import com.almadevelop.comixreader.screen.list.dialog.BaseDraggableDialog

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