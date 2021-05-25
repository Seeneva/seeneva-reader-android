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
import androidx.activity.result.ActivityResultCaller
import androidx.fragment.app.Fragment

/**
 * Router source context
 */
interface RouterContext {
    /**
     * @see ComponentActivity.startActivity
     * @see Fragment.startActivity
     */
    fun startActivity(data: Intent)
}

/**
 * Router source context which can return results
 */
interface RouterResultContext : RouterContext, ActivityResultCaller

fun ComponentActivity.asRouterContext(): RouterResultContext =
    ActivityRouterContext(this)

fun Fragment.asRouterContext(): RouterResultContext =
    FragmentRouterContext(this)

fun Context.asRouterContext(): RouterContext =
    ContextRouterContext(this)

private class ActivityRouterContext(
    private val activity: ComponentActivity
) : RouterResultContext, ActivityResultCaller by activity {
    override fun startActivity(data: Intent) {
        activity.startActivity(data)
    }
}

private class FragmentRouterContext(
    private val fragment: Fragment
) : RouterResultContext, ActivityResultCaller by fragment {
    override fun startActivity(data: Intent) {
        fragment.startActivity(data)
    }
}

private class ContextRouterContext(
    private val context: Context
) : RouterContext {
    override fun startActivity(data: Intent) {
        context.startActivity(data)
    }
}