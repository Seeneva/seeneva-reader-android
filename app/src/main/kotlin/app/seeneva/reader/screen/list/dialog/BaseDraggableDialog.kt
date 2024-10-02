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

import android.app.Dialog
import android.os.Bundle
import android.view.View
import app.seeneva.reader.R
import app.seeneva.reader.extension.setDraggableBackground
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.R as MaterialR

abstract class BaseDraggableDialog : BottomSheetDialogFragment {
    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    private val bottomSheetBehavior by lazy {
        requireDialog().findViewById<View>(MaterialR.id.design_bottom_sheet)
            .let { BottomSheetBehavior.from(it) }
    }

    init {
        setStyle(STYLE_NORMAL, R.style.AppTheme_BottomSheetDialog_NonCollapsed)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (savedInstanceState == null) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        return super.onCreateDialog(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setDraggableBackground()
    }
}