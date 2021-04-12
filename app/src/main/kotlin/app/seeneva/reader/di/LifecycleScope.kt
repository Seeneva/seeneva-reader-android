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

package app.seeneva.reader.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.KoinScopeComponent
import org.koin.core.scope.Scope
import org.koin.core.scope.newScope
import org.tinylog.kotlin.Logger
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

val Fragment.activityScope
    get() = (activity as? KoinScopeComponent)?.scope

fun Fragment.requireActivityScope(): Scope =
    requireNotNull(activityScope) { "Fragment's Activity doesn't has Koin Scope" }

val Fragment.parentFragmentScope
    get() = (parentFragment as? KoinScopeComponent)?.scope

fun Fragment.requireParentFragmentScope(): Scope =
    requireNotNull(parentFragmentScope) { "Fragment's parent Fragment doesn't has Koin Scope" }

interface LifecycleScope : KoinScopeComponent {
    fun <T : Any> autoInit(clazz: KClass<T>, qualifier: Qualifier? = null): Lazy<T>
}

/**
 * Getter scope delegate
 */
operator fun LifecycleScope.getValue(thisRef: Any?, property: KProperty<*>): Scope = scope

/**
 * Lazy object [T] initialization. Will be initialized right after Koin scope initialization
 * @param qualifier
 */
inline fun <reified T : Any> LifecycleScope.autoInit(qualifier: Qualifier? = null) =
    autoInit(T::class, qualifier)

private class LifecycleScopeImpl<T>(
    source: T,
    initState: Lifecycle.State?,
    onInit: (Scope) -> Unit
) : LifecycleScope where T : LifecycleOwner, T : KoinScopeComponent {
    private val autoInit = mutableListOf<Lazy<*>>()

    private val _scope = lazy { source.newScope(source) }

    private var onInit: ((Scope) -> Unit)? = onInit

    override val scope: Scope
        get() {
            val init = !_scope.isInitialized()

            return _scope.value.also { scope ->
                if (init) {
                    onInit?.invoke(scope)
                    onInit = null

                    autoInit.forEach { Logger.info("Auto init ${it.value}") }
                    autoInit.clear()
                }
            }
        }

    init {
        source.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (_scope.isInitialized()) {
                    closeScope()
                    Logger.info("Scope closed. Scope: $scope")
                }
            }
        })

        if (initState != null) {
            source.lifecycleScope.initScope(source.lifecycle, initState)
        }
    }

    override fun <T : Any> autoInit(clazz: KClass<T>, qualifier: Qualifier?) =
        if (_scope.isInitialized()) {
            lazyOf(scope.get(clazz))
        } else {
            lazy { scope.get(clazz, qualifier) }.also { autoInit += it }
        }

    private fun CoroutineScope.initScope(lifecycle: Lifecycle, initState: Lifecycle.State) {
        launch {
            lifecycle.whenStateAtLeast(initState) {
                if (!_scope.isInitialized()) {
                    Logger.info("Scope was initialized. Scope: $scope")
                }
            }
        }
    }
}

/**
 * Provide Koin [Scope] for the source lifecycle
 *
 * @param initState scope initializing state. Pass null to prevent lazy initialization
 * @param onInit Operation to do with scope after init was finished. (e.g. link scopes)
 */
fun <T> T.koinLifecycleScope(
    initState: Lifecycle.State? = Lifecycle.State.CREATED,
    onInit: (Scope) -> Unit = { }
): LifecycleScope where T : LifecycleOwner, T : KoinScopeComponent =
    LifecycleScopeImpl(this, initState, onInit)

class LifecycleLazyInit<P>(
    lazy: Lazy<P>,
    lifecycleOwner: LifecycleOwner,
    initState: Lifecycle.State
) : Lazy<P> by lazy {
    init {
        lifecycleOwner.lifecycleScope.init(lifecycleOwner.lifecycle, initState)
    }

    private fun CoroutineScope.init(lifecycle: Lifecycle, initState: Lifecycle.State) {
        launch {
            lifecycle.whenStateAtLeast(initState) {
                //ugly hack
                //Needed to initialize after onCreate. So it can use ViewModels
                Logger.info("$value has been init.")
            }
        }
    }
}

/**
 * Lazy value init delegate helper. Will init value when specified [initState] occurred
 * @param initState when init value
 * @param qualifier
 * @param mode
 * @param parameters
 */
inline fun <S, reified P> S.koinLifecycleInit(
    initState: Lifecycle.State = Lifecycle.State.CREATED,
    qualifier: Qualifier? = null,
    mode: LazyThreadSafetyMode = LazyThreadSafetyMode.SYNCHRONIZED,
    noinline parameters: ParametersDefinition? = null
): Lazy<P> where S : LifecycleOwner, S : KoinScopeComponent =
    LifecycleLazyInit(lazy(mode) { scope.get(qualifier, parameters) }, this, initState)

/**
 * Lazy value init delegate helper. Will init value when specified [initState] occurred
 * @param scope Koin scope instance
 * @param initState when init value
 * @param qualifier
 * @param mode
 * @param parameters
 */
inline fun <S, reified P> S.koinLifecycleInit(
    scope: Scope,
    initState: Lifecycle.State = Lifecycle.State.CREATED,
    qualifier: Qualifier? = null,
    mode: LazyThreadSafetyMode = LazyThreadSafetyMode.SYNCHRONIZED,
    noinline parameters: ParametersDefinition? = null
): Lazy<P> where S : LifecycleOwner =
    LifecycleLazyInit(scope.inject(qualifier, mode, parameters), this, initState)