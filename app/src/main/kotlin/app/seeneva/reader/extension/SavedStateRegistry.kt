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

package app.seeneva.reader.extension

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withCreated
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import kotlinx.coroutines.launch

/**
 * Register this [SavedStateRegistry.SavedStateProvider] and try to restore state if any
 * @param savedStateRegistryOwner state registry owner
 * @param key key to use for registration
 * @param restore state restore function
 */
inline fun <reified T : SavedStateRegistry.SavedStateProvider> T.registerAndRestore(
    savedStateRegistryOwner: SavedStateRegistryOwner,
    key: String = T::class.java.name,
    crossinline restore: (Bundle?) -> Unit
) {
    savedStateRegistryOwner.lifecycleScope.launch {
        savedStateRegistryOwner.withCreated {
            savedStateRegistryOwner.savedStateRegistry.also {
                it.registerSavedStateProvider(key, this@registerAndRestore)

                it.consumeRestoredStateForKey(key).also(restore)
            }
        }
    }
}