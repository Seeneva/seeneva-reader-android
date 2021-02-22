package com.almadevelop.comixreader.router

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * Router source context
 */
interface RouterContext {
    val context: Context

    /**
     * @see FragmentActivity.startActivity
     * @see Fragment.startActivity
     */
    fun startActivity(data: Intent)
}

interface RouterResultContext : RouterContext {
    /**
     * @see FragmentActivity.startActivityForResult
     * @see Fragment.startActivityForResult
     */
    fun startActivityForResult(data: Intent, requestCode: Int)
}

fun ComponentActivity.asRouterContext() = object : RouterResultContext {
    override val context: Context
        get() = this@asRouterContext

    override fun startActivity(data: Intent) {
        this@asRouterContext.startActivity(data)
    }

    override fun startActivityForResult(data: Intent, requestCode: Int) {
        //wait while Fragment will support `registerForActivityResult` too
        this@asRouterContext.startActivityForResult(data, requestCode)
    }
}

fun Fragment.asRouterContext() = object : RouterResultContext {
    override val context: Context
        get() = this@asRouterContext.requireContext()

    override fun startActivity(data: Intent) {
        this@asRouterContext.startActivity(data)
    }

    override fun startActivityForResult(data: Intent, requestCode: Int) {
        this@asRouterContext.startActivityForResult(data, requestCode)
    }
}

fun Context.asRouterContext() = object : RouterContext {
    override val context: Context
        get() = this@asRouterContext

    override fun startActivity(data: Intent) {
        this@asRouterContext.startActivity(data)
    }
}