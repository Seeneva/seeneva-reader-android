package com.almadevelop.comixreader.screen.list.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import androidx.fragment.app.DialogFragment
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.di.parentFragmentScope
import com.almadevelop.comixreader.logic.entity.ComicListItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

class ComicRenameDialog : DialogFragment() {
    @Suppress("InflateParams")
    private val titleInput by lazy {
        LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_comic_rename,
            null
        ) as TextInputLayout
    }

    private val titleEditText
        get() = titleInput.editText!!

    private val canFinish: Boolean
        get() = dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled

    private val imm by lazy { requireContext().getSystemService<InputMethodManager>()!! }

    private val callback by lazy { parentFragmentScope?.getOrNull<Callback>() }

    private val clickListener = DialogInterface.OnClickListener { _, which ->
        when (which) {
            DialogInterface.BUTTON_POSITIVE -> onFinish()
            DialogInterface.BUTTON_NEGATIVE -> dialog.cancel()
        }
    }

    private val textChangeListener = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
            onTitleChanged(s)
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        }
    }

    /**
     * Id of the comic book which should be renamed
     */
    private val comicId by lazy { requireNotNull(arguments).getComicId() }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        titleEditText.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    onFinish()
                    true
                }
                else -> false
            }
        }

        (savedInstanceState ?: requireNotNull(arguments) { "Use newInstance!" }).also {
            titleEditText.setText(it.getComicTitle())
        }

        titleEditText.post {
            if (titleEditText.requestFocus()) {
                titleEditText.setSelection(titleEditText.length())
                imm.showSoftInput(titleEditText, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.comic_list_rename)
            .setPositiveButton(R.string.all_ok, clickListener)
            .setNegativeButton(R.string.all_cancel, clickListener)
            .setView(titleInput)
            .setCancelable(false)
            .create()
            .apply {
                setCanceledOnTouchOutside(false)

                setOnShowListener {
                    onTitleChanged(titleEditText.text)

                    titleEditText.addTextChangedListener(textChangeListener)
                }
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.saveComicTitle(titleEditText.text)
    }

    override fun getDialog(): AlertDialog {
        return super.getDialog() as AlertDialog
    }

    private fun onFinish() {
        if (canFinish) {
            callback?.onTitleRenamed(comicId, titleEditText.text.toString())
            dismiss()
        }
    }

    private fun onTitleChanged(title: CharSequence) {
        if (title.isEmpty()) {
            titleInput.error = getString(R.string.rename_err_empty_title)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
        } else {
            titleInput.error = null
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
        }
    }

    interface Callback {
        /**
         * Title of the comic book was renamed
         * @param id id of the comic book
         * @param newTitle buildNew title of the comic book
         */
        fun onTitleRenamed(id: Long, newTitle: String)
    }

    companion object {
        private const val STATE_TITLE = "title"
        private const val STATE_ID = "id"

        /**
         * @param comic comic book to rename
         */
        fun newInstance(comic: ComicListItem) =
            ComicRenameDialog().apply {
                arguments = Bundle().apply { saveComicData(comic.id, comic.title) }
            }

        private fun Bundle.saveComicData(id: Long, title: CharSequence) {
            putLong(STATE_ID, id)
            saveComicTitle(title)
        }

        private fun Bundle.saveComicTitle(title: CharSequence) {
            putCharSequence(STATE_TITLE, title)
        }

        private fun Bundle.getComicId() = getLong(STATE_ID)
        private fun Bundle.getComicTitle() =
            requireNotNull(getCharSequence(STATE_TITLE)) { "Set comic book title!" }
    }
}