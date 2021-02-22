package com.almadevelop.comixreader.logic.image.coil

import coil.request.Disposable
import com.almadevelop.comixreader.logic.image.ImageLoadingTask

internal class CoilImageLoadingTask(private val coilRequest: Disposable) : ImageLoadingTask {
    override val isDisposed: Boolean
        get() = coilRequest.isDisposed

    override fun dispose() {
        coilRequest.dispose()
    }
}

internal fun Disposable.asTask(): ImageLoadingTask =
    CoilImageLoadingTask(this)