package com.almadevelop.comixreader.extension

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes

fun <T: View> ViewGroup.inflate(
    @LayoutRes resId: Int,
    attach: Boolean = false,
    inflater: LayoutInflater = LayoutInflater.from(context)
): T = inflater.inflate(resId, this, attach) as T