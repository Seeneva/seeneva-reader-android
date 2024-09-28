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

package app.seeneva.reader.screen.list.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.plusAssign
import app.seeneva.reader.R
import app.seeneva.reader.di.parentFragmentScope
import app.seeneva.reader.extension.inflate
import app.seeneva.reader.logic.comic.AddComicBookMode
import app.seeneva.reader.logic.R as LogicR

class AddModeSelectorDialog : BaseDraggableDialog() {
    private val addModes = AddComicBookMode.entries

    private val callback by lazy { parentFragmentScope?.getOrNull<Callback>() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_add_mode_selector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val containerView = view.findViewById<ViewGroup>(R.id.container)

        val onModeClickListener = View.OnClickListener {
            callback?.onAddModeSelected(it.tag as AddComicBookMode)

            dismiss()
        }

        addModes.forEach { mode ->
            containerView += containerView.inflate<View>(R.layout.layout_add_mode_item)
                .also {
                    val (title, description) = mode.data

                    it.findViewById<TextView>(R.id.title).text = title
                    it.findViewById<TextView>(R.id.description).text = description

                    it.setOnClickListener(onModeClickListener)

                    it.tag = mode
                }
        }
    }

    interface Callback {
        fun onAddModeSelected(selectedMode: AddComicBookMode)
    }

    private data class AddModeData(val title: CharSequence, val description: CharSequence)

    private val AddComicBookMode.data: AddModeData
        get() {
            return when (this) {
                AddComicBookMode.Import ->
                    AddModeData(
                        getString(LogicR.string.add_mode_import),
                        getString(LogicR.string.add_mode_import_descr)
                    )
                AddComicBookMode.Link ->
                    AddModeData(
                        getString(LogicR.string.add_mode_link),
                        getString(LogicR.string.add_mode_link_descr)
                    )
            }
        }

    companion object {
        fun newInstance() = AddModeSelectorDialog()
    }
}