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

package app.seeneva.reader.logic.image.target

import android.graphics.drawable.Drawable
import androidx.annotation.MainThread

/**
 * Target of a [app.seeneva.reader.logic.image.ImageLoader]
 *
 * @param T type of a loading image
 */
interface ImageLoaderTarget<T> {
    /**
     * Called when state of an image loader process has been changed
     * @param state state of an image loader
     */
    @MainThread
    fun onImageLoadStateChanged(state: State<T>)

    sealed interface State<out T> {
        /**
         * Image loaded
         */
        data class Success<T>(val result: T) : State<T>

        /**
         * Image loading error
         */
        data class Error(override val placeholder: Drawable?) : State<Nothing>, WithPlaceholder

        /**
         * Image load started
         */
        data class Loading(override val placeholder: Drawable?) : State<Nothing>, WithPlaceholder

        /**
         * Previously loaded image should be removed
         */
        object Clear : State<Nothing>, WithPlaceholder {
            override val placeholder: Drawable?
                get() = null
        }

        interface WithPlaceholder {
            /**
             * Optional state placeholder
             */
            val placeholder: Drawable?
        }
    }
}