package com.almadevelop.comixreader.widget

import android.annotation.TargetApi
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.get
import androidx.core.view.isEmpty
import androidx.core.view.isGone
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.extension.inflate

class ContentMessageView : FrameLayout {
    private lateinit var currentState: State

    private val contentState by lazy { ContentState(this[0]) }
    private val messageState by lazy { MessageState(this) }
    private val loadingState by lazy { LoadingState(this) }

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(
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

    fun showMessage(message: CharSequence, icon: Drawable?) {
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

    private class MessageState(parent: ViewGroup) : State {
        override val view: View = parent.inflate(R.layout.layout_fullscreen_message)

        private val iconView = view.findViewById<ImageView>(R.id.icon)
        private val messageView = view.findViewById<TextView>(R.id.message)

        override fun onChange() {
            super.onChange()

            iconView.setImageDrawable(null)
        }

        fun setMessage(message: CharSequence, icon: Drawable?) {
            messageView.text = message

            iconView.setImageDrawable(icon)
            iconView.isGone = icon == null
        }
    }

    private class LoadingState(parent: ViewGroup) : State {
        override val view: View = parent.inflate(R.layout.layout_fullscreen_progress)
    }
}