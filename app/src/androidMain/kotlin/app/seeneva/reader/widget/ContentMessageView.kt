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

package app.seeneva.reader.widget

import android.annotation.TargetApi
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.get
import androidx.core.view.isEmpty
import androidx.core.view.isGone
import app.seeneva.reader.databinding.LayoutFullscreenMessageBinding
import app.seeneva.reader.databinding.LayoutFullscreenProgressBinding

class ContentMessageView : FrameLayout {
    private val inflater by lazy { LayoutInflater.from(context) }

    private lateinit var currentState: State

    private val contentState by lazy { ContentState(this[0]) }
    private val messageState by lazy { MessageState(inflater, this) }
    private val loadingState by lazy { LoadingState(inflater, this) }

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    )

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        if (isEmpty()) {
            super.addView(child, index, params)
            if (!::currentState.isInitialized) {
                currentState = contentState
            }
        } else {
            throw IllegalStateException("View already has content view")
        }
    }

    fun showContent() {
        setNewState(contentState)
    }

    fun showMessage(@StringRes messageResId: Int, @DrawableRes iconResId: Int = 0) {
        val message = resources.getString(messageResId)

        val icon = if (iconResId != 0) {
            AppCompatResources.getDrawable(context, iconResId)
        } else {
            null
        }

        showMessage(message, icon)
    }

    fun showMessage(message: CharSequence, icon: Drawable? = null) {
        messageState.setMessage(message, icon)

        setNewState(messageState)
    }

    fun showLoading() {
        setNewState(loadingState)
    }

    private fun setNewState(state: State) {
        if (currentState === state) {
            return
        }

        currentState.onChange()
        removeView(currentState.view)

        currentState = state
        addView(state.view)
    }

    private interface State {
        val view: View

        fun onChange() {
            //do nothing
        }
    }

    private class ContentState(override val view: View) : State

    private class MessageState(inflater: LayoutInflater, parent: ViewGroup) : State {
        private val binding = LayoutFullscreenMessageBinding.inflate(inflater, parent, false)

        override val view
            get() = binding.root

        override fun onChange() {
            super.onChange()

            binding.icon.setImageDrawable(null)
        }

        fun setMessage(message: CharSequence, icon: Drawable?) {
            binding.message.text = message

            binding.icon.setImageDrawable(icon)
            binding.icon.isGone = icon == null
        }
    }

    private class LoadingState(inflater: LayoutInflater, parent: ViewGroup) : State {
        private val binding = LayoutFullscreenProgressBinding.inflate(inflater, parent, false)

        override val view
            get() = binding.root
    }
}