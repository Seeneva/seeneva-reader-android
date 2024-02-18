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

package app.seeneva.reader.screen.viewer.page

import android.os.Bundle
import android.view.View
import androidx.annotation.MainThread
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import app.seeneva.reader.R
import app.seeneva.reader.binding.getValue
import app.seeneva.reader.binding.viewBinding
import app.seeneva.reader.databinding.FragmentViewerHelpBinding
import app.seeneva.reader.di.requireParentFragmentScope
import app.seeneva.reader.extension.setOnApplyWindowInsetsListenerByPadding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.properties.Delegates

class PageViewerHelperFragment : Fragment(R.layout.fragment_viewer_help) {
    private val viewBinder by viewBinding(FragmentViewerHelpBinding::bind)

    private val callback by lazy { requireParentFragmentScope().getOrNull<Callback>() }

    private val indicatorDrawable by lazy { viewBinder.tipView.compoundDrawables[3] }

    private val tipPadding by lazy { resources.getDimension(R.dimen.viewer_help_padding) }

    /**
     * Insets which should be applied to tip indicator
     */
    private var tipInsets by Delegates.observable(Insets.NONE) { _, _, _ ->
        if (currentTip != null) {
            updateTipLocation()
        }
    }

    private var currentTip: Tip? = null

    private val _lastFinishedTipFLow = MutableStateFlow(NO_TIP_ID)

    /**
     * Flow of the last finished tip id
     * @see NO_TIP_ID
     */
    val lastFinishedTipFLow = _lastFinishedTipFLow.asStateFlow()

    private val onTipClick = View.OnClickListener {
        tipCompleted()
    }

    private val onTipLongClick = View.OnLongClickListener {
        tipCompleted()

        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            _lastFinishedTipFLow.value = savedInstanceState.getInt(KEY_TIP_ID, NO_TIP_ID)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("ClickableViewAccessibility")
        viewBinder.root.setOnTouchListener { _, _ -> true }

        viewBinder.toolbar.apply {
            ViewCompat.setElevation(this, .0f)

            setNavigationOnClickListener {
                parentFragmentManager.commit { remove(this@PageViewerHelperFragment) }
                callback?.onDismissClick()
            }

            setOnApplyWindowInsetsListenerByPadding { v, initialPadding, insets ->
                val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

                v.updatePadding(
                    left = initialPadding.left + systemInsets.left,
                    right = initialPadding.right + systemInsets.right,
                    top = initialPadding.top + systemInsets.top
                )

                insets
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(viewBinder.tipView) { _, insets ->
            tipInsets = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())

            insets
        }

        if (currentTip != null) {
            showTipInner()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TIP_ID, _lastFinishedTipFLow.value)
    }

    /**
     * Show tip to user
     * @param id tip id
     * @param cx tip X center
     * @param cy tip Y center
     * @param type type of the tip
     */
    @MainThread
    fun showTip(id: Int, cx: Float, cy: Float, type: Type) {
        currentTip = Tip(id, cx, cy, type)

        if (view != null) {
            showTipInner()
        }
    }

    private fun showTipInner() {
        val tip = currentTip ?: return

        viewBinder.tipView.apply {
            when (tip.type) {
                Type.TAP -> {
                    setText(R.string.viewer_help_tap)
                    setOnClickListener(onTipClick)
                    setOnLongClickListener(null)
                }
                Type.HOLD -> {
                    setText(R.string.viewer_help_hold)
                    setOnClickListener(null)
                    setOnLongClickListener(onTipLongClick)
                }
            }
        }

        updateTipLocation()
    }

    /**
     * Update tip indicator location
     */
    private fun updateTipLocation() {
        val tip = currentTip ?: return

        viewBinder.tipView.doOnLayout {
            // difference between full view height and indicator height
            val hD = it.height - indicatorDrawable.intrinsicHeight

            it.x = (tip.cx - it.width * 0.5f).coerceIn(
                tipPadding + tipInsets.left,
                viewBinder.root.width.toFloat() - tipPadding - tipInsets.right - it.width
            )

            it.y = (tip.cy - (it.height + hD) * 0.5f).coerceIn(
                tipPadding + tipInsets.top,
                viewBinder.root.height.toFloat() - tipPadding - tipInsets.bottom - it.height
            )
        }
    }

    /**
     * Tip was completed by user
     */
    private fun tipCompleted() {
        val id = checkNotNull(currentTip).id

        currentTip = null

        _lastFinishedTipFLow.value = id
    }

    enum class Type {
        /**
         * User should tap on a tip
         */
        TAP,

        /**
         * User should hold a tip
         */
        HOLD
    }

    interface Callback {
        /**
         * User clicked on dismiss help screen button
         */
        fun onDismissClick()
    }

    private data class Tip(val id: Int, val cx: Float, val cy: Float, val type: Type)

    companion object {
        const val NO_TIP_ID = Int.MIN_VALUE

        private const val KEY_TIP_ID = "tip_id"
    }
}