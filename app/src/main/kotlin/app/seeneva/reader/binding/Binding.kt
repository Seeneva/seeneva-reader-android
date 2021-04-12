/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
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

package app.seeneva.reader.binding

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
    private val lifecycleProvider: () -> Lifecycle,
    private val bindingProvider: () -> B,
    private val doOnDestroy: B.() -> Unit = {}
) : BindingDelegate<B> {
    // Detached fragments can reuse same delegate after reattach
    // So, be prepared :)
    private var savedBinding: B? = null

    override val binding: B
        get() = savedBinding ?: initializeViewBinding()

    private val destroyObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)

            savedBinding?.doOnDestroy()

            if (savedBinding != null) {
                Logger.info("View binding was destroyed: $savedBinding")
            }

            savedBinding = null

            owner.lifecycle.removeObserver(this)
        }
    }

    private fun initializeViewBinding(): B {
        val lifecycle = lifecycleProvider()

        check(lifecycle.currentState != Lifecycle.State.DESTROYED) {
            "Can't init view binding. Destroyed state"
        }

        lifecycle.addObserver(destroyObserver)

        return bindingProvider().also {
            Logger.info("View binding initialized $it")

            savedBinding = it
        }
    }
}

/**
 * Default [ComponentActivity]'s ViewBinding delegate
 */
fun <B : ViewBinding> ComponentActivity.viewBinding(onInitBinding: (View) -> B): BindingDelegate<B> =
    viewBinding(config(onInitBinding))

/**
 * Configurable [ComponentActivity]'s ViewBinding delegate
 */
fun <B : ViewBinding> ComponentActivity.viewBinding(config: ViewBindingConfig<B>): BindingDelegate<B> =
    LifecycleBindingDelegate({ lifecycle }, config.onInitBinding, config.onDestroy)

/**
 * Default [Fragment]'s ViewBinding delegate
 */
fun <B : ViewBinding> Fragment.viewBinding(onInitBinding: (View) -> B): BindingDelegate<B> =
    viewBinding(config(onInitBinding))

/**
 * Configurable [Fragment]'s ViewBinding delegate
 */
fun <B : ViewBinding> Fragment.viewBinding(
    config: ViewBindingConfig<B>
): BindingDelegate<B> =
    LifecycleBindingDelegate(
        { viewLifecycleOwner.lifecycle },
        config.onInitBinding,
        config.onDestroy
    )

/**
 * [ComponentActivity]'s ViewBinding delegate config
 * @param onInitBinding init binding from provided [View] instance
 * @return config for ViewBinding delegate
 */
fun <B : ViewBinding> ComponentActivity.config(onInitBinding: (View) -> B): ViewBindingConfig<B> =
    ViewBindingConfigInner {
        val contentView = findViewById<View>(Window.ID_ANDROID_CONTENT)

        onInitBinding(
            when {
                contentView is ViewGroup && contentView.isNotEmpty() -> contentView[0]
                else -> contentView
            }
        )
    }

/**
 * [Fragment]'s ViewBinding delegate config
 * @param onInitBinding init binding from provided [View] instance
 * @return config for ViewBinding delegate
 */
fun <B : ViewBinding> Fragment.config(onInitBinding: (View) -> B): ViewBindingConfig<B> =
    ViewBindingConfigInner { onInitBinding(requireView()) }

/**
 * ViewBinding delegate config
 * @param onInitBinding init binding
 * @return config for ViewBinding delegate
 */
fun <B : ViewBinding> configCustom(onInitBinding: () -> B): ViewBindingConfig<B> =
    ViewBindingConfigInner(onInitBinding)

/**
 * Specify function which should be called before ViewBinding destroy
 * @param onDestroy
 */
infix fun <B : ViewBinding> ViewBindingConfig<B>.doOnDestroy(onDestroy: B.() -> Unit): ViewBindingConfig<B> {
    this.onDestroy = onDestroy

    return this
}

interface ViewBindingConfig<B : ViewBinding> {
    val onInitBinding: () -> B
    var onDestroy: B.() -> Unit
}

private class ViewBindingConfigInner<B : ViewBinding>(override val onInitBinding: () -> B) :
    ViewBindingConfig<B> {
    override var onDestroy: B.() -> Unit = {}
}