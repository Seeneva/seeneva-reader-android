/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
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

package app.seeneva.reader.router

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