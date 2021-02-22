package com.almadevelop.comixreader.logic.image

interface ImageLoadingTask {
    val isDisposed: Boolean

    fun dispose()
}