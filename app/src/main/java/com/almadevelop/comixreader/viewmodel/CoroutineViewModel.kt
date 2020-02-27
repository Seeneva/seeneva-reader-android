package com.almadevelop.comixreader.viewmodel

import androidx.lifecycle.ViewModel
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

abstract class CoroutineViewModel(
    protected val dispatchers: Dispatchers,
    job: Job = Job()
) : ViewModel(), CoroutineScope {
    override val coroutineContext = job + dispatchers.main

    override fun onCleared() {
        super.onCleared()
        cancel()
    }
}