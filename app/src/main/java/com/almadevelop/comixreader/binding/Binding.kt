package com.almadevelop.comixreader.binding

import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.core.view.get
import androidx.core.view.isNotEmpty
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import org.tinylog.kotlin.Logger
import kotlin.reflect.KProperty

interface BindingDelegate<B : ViewBinding> {
    val binding: B
}

operator fun <B : ViewBinding> BindingDelegate<B>.getValue(
    thisRef: Any,
    property: KProperty<*>
): B = binding

private class LifecycleBindingDelegate<B : ViewBinding>(
    lifecycleProvider: Lazy<Lifecycle>,
    private val bindingProvider: () -> B,
) : BindingDelegate<B> {
    private val lifecycle by lifecycleProvider

    // Detached fragments can reuse same delegate after reattach
    // So, be prepared :)
    private var savedBinding: B? = null

    override val binding: B
        get() = savedBinding ?: bindingProvider().also {
            Logger.debug("View binding initialized $it")

            savedBinding = it

            lifecycle.observeDestroyedState()
        }

    private fun Lifecycle.observeDestroyedState() {
        addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)

                if (savedBinding != null) {
                    Logger.debug("View binding was destroyed: $savedBinding")
                }

                savedBinding = null

                // onDestroy can be called multiple times in case of Fragments
                // lifecycle.removeObserver(this)
            }
        })
    }
}

fun <B : ViewBinding> ComponentActivity.viewBinding(onInitBinding: (View) -> B): BindingDelegate<B> =
    LifecycleBindingDelegate(lazyOf(lifecycle)) {
        val contentView = findViewById<View>(Window.ID_ANDROID_CONTENT)

        onInitBinding(
            when {
                contentView is ViewGroup && contentView.isNotEmpty() -> contentView[0]
                else -> contentView
            }
        )
    }

fun <B : ViewBinding> ComponentActivity.viewBindingCustom(onInitBinding: () -> B): BindingDelegate<B> =
    LifecycleBindingDelegate(lazyOf(lifecycle), onInitBinding)

fun <B : ViewBinding> Fragment.viewBinding(onInitBinding: (View) -> B): BindingDelegate<B> =
    LifecycleBindingDelegate(lazy { viewLifecycleOwner.lifecycle }) { onInitBinding(requireView()) }

fun <B : ViewBinding> Fragment.viewBindingCustom(onInitBinding: () -> B): BindingDelegate<B> =
    LifecycleBindingDelegate(lazy { viewLifecycleOwner.lifecycle }, onInitBinding)