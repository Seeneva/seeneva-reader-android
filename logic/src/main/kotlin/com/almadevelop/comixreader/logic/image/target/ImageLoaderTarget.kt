package com.almadevelop.comixreader.logic.image.target

import android.graphics.drawable.Drawable
import androidx.annotation.MainThread

/**
 * Target of a [com.almadevelop.comixreader.logic.image.ImageLoader]
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

    sealed class State<out T> {
        /**
         * Image loaded
         */
        data class Success<T>(val result: T) : State<T>()

        /**
         * Image loading error
         */
        data class Error(override val placeholder: Drawable?) : State<Nothing>(), WithPlaceholder

        /**
         * Image load started
         */
        data class Loading(override val placeholder: Drawable?) : State<Nothing>(), WithPlaceholder

        /**
         * Previously loaded image should be removed
         */
        object Clear : State<Nothing>(), WithPlaceholder {
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