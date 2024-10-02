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

package app.seeneva.reader.di

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.get
import org.koin.android.ext.android.getKoin
import org.koin.android.scope.AndroidScopeComponent
import org.koin.androidx.scope.ScopeHandlerViewModel
import org.koin.androidx.scope.retainedScopeId
import org.koin.core.Koin
import org.koin.core.component.getScopeName
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID

/**
 * Create a retain scope using [ScopeHandlerViewModel]
 *
 * @param koin Koin instance
 * @param scopeId ID for the new scope
 * @param scopeName name for the new scope
 */
fun ViewModelStoreOwner.createRetainScope(
    koin: Koin,
    scopeId: ScopeID,
    scopeName: Qualifier = getScopeName()
): Scope {
    if (this !is AndroidScopeComponent) {
        error("Parent should implement AndroidScopeComponent")
    }

    val vm = ViewModelProvider(this).get<ScopeHandlerViewModel>()

    return vm.scope ?: koin.createScope(scopeId, scopeName).also { vm.scope = it }
}

/**
 * Implementation of [ComponentActivity.createActivityRetainScope] that allows to set custom [scopeName]
 *
 * @param scopeName name for the new scope
 */
fun ComponentActivity.createActivityRetainScope(scopeName: Qualifier = getScopeName()): Scope =
    createRetainScope(koin = getKoin(), scopeId = retainedScopeId(), scopeName = scopeName)