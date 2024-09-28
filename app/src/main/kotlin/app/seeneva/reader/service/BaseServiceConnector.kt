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

package app.seeneva.reader.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.seeneva.reader.common.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import org.tinylog.kotlin.Logger

/**
 * @param context
 * @param parent parent scope [Job] if any
 * @param dispatchers
 * @param serviceClazz class of the Android Service which should be connected
 *
 * @param S Android Service type
 * @param B Android Service binder type
 */
abstract class BaseServiceConnector<S : Service, B : Any>(
    context: Context,
    parent: Job?,
    dispatchers: Dispatchers,
    serviceClazz: Class<S>,
) {
    protected val context: Context = context.applicationContext

    /**
     * Service binder flow. It is **hot** flow which will be active until have subscribers
     */
    protected val binderFlow = callbackFlow {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                Logger.info("Service '${serviceClazz.name}' binder connected")

                @Suppress("UNCHECKED_CAST")
                trySend(BinderState.Connected(service as B))
            }

            override fun onServiceDisconnected(name: ComponentName) {
                trySend(BinderState.Disconnected)
            }

            override fun onBindingDied(name: ComponentName) {
                // throw special exception to invoke Flow retry later
                throw BindingDied(name)
            }

            override fun onNullBinding(name: ComponentName) {
                throw IllegalStateException("Android return null Service binding")
            }
        }

        Logger.info("Trying to connect to Service '${serviceClazz.name}' binder")

        connect(connection, serviceClazz)

        awaitClose {
            Logger.info("Disconnect from Service '${serviceClazz.name}' binder")

            disconnect(connection)
        }

    }.flowOn(dispatchers.mainImmediate)
        .conflate()
        .retryWhen { cause, _ ->
            when (cause) {
                is BindingDied -> {
                    //emit disconnected task and retry this flow
                    emit(BinderState.Disconnected)
                    true
                }
                else -> false
            }
        }
        .stateIn(
            CoroutineScope(dispatchers.io + Job(parent)),
            WhileSubscribed(replayExpirationMillis = 0),
            BinderState.Disconnected
        )

    /**
     * Subscribe to [binderFlow] and wait till it will emit connected Service binder
     * @param action action to invoke when Service binder connect
     */
    protected suspend inline fun <R> onBind(crossinline action: suspend (B) -> R): R =
        binderFlow.filterIsInstance<BinderState.Connected<B>>()
            .mapLatest { action(it.binder) }
            .take(1)
            .single()

    /**
     * Try to connect to provided Service
     */
    private fun connect(
        connection: ServiceConnection,
        clazz: Class<S>
    ) {
        val success = context.bindService(
            Intent(context, clazz),
            connection,
            Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        )

        check(success) { "Can't connect to Android Service ${clazz.name}" }
    }

    private fun disconnect(connection: ServiceConnection) {
        context.unbindService(connection)
    }

    /**
     * Special exception in case if binder died
     */
    private class BindingDied(componentName: ComponentName) :
        RuntimeException("Android binding died: $componentName")

    protected sealed interface BinderState<out B : Any> {
        /**
         * Binder connected and ready to use
         * @param binder binder reference
         */
        data class Connected<out B : Any>(val binder: B) : BinderState<B>

        /**
         * Binder was disconnected or it is not ready yet
         */
        data object Disconnected : BinderState<Nothing>
    }
}

