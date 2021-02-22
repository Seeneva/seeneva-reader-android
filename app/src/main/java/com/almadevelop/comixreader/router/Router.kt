package com.almadevelop.comixreader.router

import android.content.Intent
import kotlinx.coroutines.flow.Flow

interface Router

interface ResultRouter<R>: Router {
    val resultFlow: Flow<R>

    //TODO get an eye into https://developer.android.com/reference/androidx/activity/result/package-summary and wait for stable release
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
}