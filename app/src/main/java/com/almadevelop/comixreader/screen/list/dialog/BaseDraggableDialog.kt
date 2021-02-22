package com.almadevelop.comixreader.screen.list.dialog

import android.os.Bundle
import android.view.View
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.extension.setDraggableBackground
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

abstract class BaseDraggableDialog : BottomSheetDialogFragment() {
    private val bottomSheetBehavior by lazy {
        requireDialog().findViewById<View>(R.id.design_bottom_sheet)
            .let { BottomSheetBehavior.from(it) }
    }

    init {
        setStyle(STYLE_NORMAL, R.style.AppTheme_BottomSheetDialog_NonCollapsed)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

       bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setDraggableBackground()
    }
}