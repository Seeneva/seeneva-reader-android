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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.whenStateAtLeast
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

//TODO observe functions should be replaced by 'repeatOnLifecycle'
// after the 'androidx.lifecycle' 2.4.0 stable release
// https://developer.android.com/jetpack/androidx/releases/lifecycle#version_240_2

/**
 * Start [Flow] values collector. Values will be send to [observer] only when
 * current [Lifecycle.State] is at least [minState]
 * @param lifecycleOwner used to start coroutine and check current [Lifecycle.State]
 * @param minState state when [observer] starts to receive [Flow] values
 * @param observer function which will receive [Flow] values on [lifecycleOwner] dispatcher.
 * @return started [Job] which can be cancelled
 */
inline fun <T> Flow<T>.observe(
    lifecycleOwner: LifecycleOwner,
    minState: Lifecycle.State = Lifecycle.State.STARTED,
    crossinline observer: suspend (T) -> Unit
): Job = observe(lifecycleOwner.lifecycle, minState, observer)

/**
 * @see observe
 */
inline fun <T> Flow<T>.observe(
    lifecycle: Lifecycle,
    minState: Lifecycle.State = Lifecycle.State.STARTED,
    crossinline observer: suspend (T) -> Unit
): Job = lifecycle.coroutineScope
    .launch {
        collectLatest {
            lifecycle.whenStateAtLeast(minState) {
                observer(it)
            }
        }
    }