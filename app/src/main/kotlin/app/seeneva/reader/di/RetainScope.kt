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

import androidx.lifecycle.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.scope.ScopeHandlerViewModel
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.tinylog.kotlin.Logger
import kotlin.reflect.KProperty

/**
 * Scope which will outlive Android lifecycle onDestroy event
 */
interface RetainScope {
    val scope: Scope
}

operator fun RetainScope.getValue(thisRef: Any, property: KProperty<*>): Scope = scope

private class RetainScopeImpl<T>(
    source: T,
    qualifier: Qualifier,
    koin: Koin
) : RetainScope where T : ViewModelStoreOwner, T : LifecycleOwner {
    private val _scope = lazy {
        val vm = source.getViewModel<ScopeHandlerViewModel>()

        vm.scope.let { scope ->
            scope ?: koin
                .createScope(
                    // ViewModel's `onCleared` method called after some small amount of time after Activity was destroyed.
                    //  So I need to add source hash code to prevent `scope already created` exceptions
                    //  in case if user was too fast to open the same Activity again
                    "${source::class.simpleName}_${this::class.simpleName}_${
                        System.identityHashCode(source)
                    }", qualifier
                )
                .also {
                    Logger.info("Retain scope was created. Scope: $it")

                    vm.scope = it
                }
        }
    }

    override val scope by _scope

    init {
        source.lifecycleScope.initScope(source.lifecycle)
    }

    private fun CoroutineScope.initScope(lifecycle: Lifecycle) {
        launch {
            lifecycle.withCreated {
                if (!_scope.isInitialized()) {
                    Logger.info("Retain scope init finished. Scope: $scope")
                }
            }
        }
    }
}

inline fun <reified T> T.koinRetainScope(): RetainScope
        where T : ViewModelStoreOwner, T : LifecycleOwner, T : KoinComponent =
    koinRetainScope(named<T>())

fun <T> T.koinRetainScope(qualifier: Qualifier): RetainScope
        where T : ViewModelStoreOwner, T : LifecycleOwner, T : KoinComponent =
    RetainScopeImpl(this, qualifier, getKoin())