package com.almadevelop.comixreader.screen.list.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.plusAssign
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.di.parentFragmentScope
import com.almadevelop.comixreader.extension.inflate
import com.almadevelop.comixreader.logic.comic.AddComicBookMode

class AddModeSelectorDialog : BaseDraggableDialog() {
    private val addModes = AddComicBookMode.values()

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
                        getString(R.string.add_mode_import),
                        getString(R.string.add_mode_import_descr)
                    )
                AddComicBookMode.Link ->
                    AddModeData(
                        getString(R.string.add_mode_link),
                        getString(R.string.add_mode_link_descr)
                    )
            }
        }

    companion object {
        fun newInstance() = AddModeSelectorDialog()
    }
}