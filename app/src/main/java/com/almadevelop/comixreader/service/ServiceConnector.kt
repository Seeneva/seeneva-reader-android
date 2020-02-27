package com.almadevelop.comixreader.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ActorScope
import kotlinx.coroutines.channels.actor
import kotlin.properties.Delegates

interface ServiceConnector {
    /**
     * Unbind binded service if any
     */
    suspend fun unbind()

    /**
     * Unbind the service and dispose any resources. You can't use this connector any more
     */
    fun close()
}

/**
 * @param B Service binder type
 */
abstract class BaseServiceConnector<B : Any>(
    context: Context,
    dispatchers: Dispatchers
) : ServiceConnector, CoroutineScope {
    override val coroutineContext = Job() + dispatchers.main

    protected val context: Context = context.applicationContext

    private val binderActor =
        actor<BinderAction<B>> { StateActorScope(context, this).proceed() }

    override suspend fun unbind() {
        BinderAction.Unbind<B>().send()
    }

    override fun close() {
        cancel()
    }

    /**
     * Wait while provided service bound
     *
     * @param clazz class of the desired Service
     * @return result service binder
     */
    protected suspend fun <T : Service> bind(clazz: Class<T>): B =
        BinderAction.Bind<B, T>(clazz).send()

    private suspend fun <A, R> A.send(): R where A : BinderAction<B>, A : BinderAction.Completable<R> {
        //need to get parent job
        return coroutineScope {
            //set parent
            parentJob = coroutineContext[Job]

            binderActor.send(this@send)

            job.await() //await will be cancelled if parent Job has been cancelled
        }
    }

    private sealed class BinderAction<B : Any> {
        var parentJob by Delegates.vetoable<Job?>(null, { _, old, _ ->
            old == null
        })

        interface Completable<R> {
            val job: CompletableDeferred<R>
        }

        class Bind<B : Any, T : Service>(val clazz: Class<T>) : BinderAction<B>(), Completable<B> {
            override val job by lazy { CompletableDeferred<B>(parentJob) }
        }

        class Unbind<B : Any> : BinderAction<B>(), Completable<Unit> {
            override val job by lazy { CompletableDeferred<Unit>(parentJob) }
        }
    }


    private class StateActorScope<B : Any>(
        private val context: Context,
        scope: ActorScope<BinderAction<B>>
    ) : ActorScope<BinderAction<B>> by scope {
        private var binder: B? = null

        private val connector = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                @Suppress("UNCHECKED_CAST")
                binder = (service as B)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                binder = null
            }

            override fun onBindingDied(name: ComponentName) {
                binder = null
            }

            override fun onNullBinding(name: ComponentName) {
                throw IllegalStateException("Can't connect to the service's binder")
            }
        }

        private fun <T : Service> connect(clazz: Class<T>): Boolean {
            return context.bindService(
                Intent(context, clazz),
                connector,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
            )
        }

        private suspend fun bind(action: BinderAction.Bind<B, *>) {
            var currentBinder = binder

            if (currentBinder == null) {
                if (!connect(action.clazz)) {
                    action.job.completeExceptionally(IllegalStateException("Can't bind service ${action.clazz.name}"))
                } else {
                    while (currentBinder == null && action.job.isActive) {
                        currentBinder = binder
                        yield()
                    }
                }
            }

            if (action.job.isActive) {
                action.job.complete(requireNotNull(currentBinder))
            }
        }

        private fun unbind(action: BinderAction.Unbind<B>) {
            if (binder != null) {
                context.unbindService(connector)
                binder = null
            }

            if (action.job.isActive) {
                action.job.complete(Unit)
            }
        }

        suspend fun proceed() {
            try {
                for (action in channel) {
                    when (action) {
                        is BinderAction.Bind<B, *> -> bind(action)
                        is BinderAction.Unbind<B> -> unbind(action)
                    }
                }
            } finally {
                //at any case unbind from service
                context.unbindService(connector)
            }
        }
    }
}

