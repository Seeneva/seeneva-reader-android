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

package app.seeneva.reader.logic.storage

import app.seeneva.reader.logic.storage.SingleObjectStorageImpl.Key
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import org.tinylog.kotlin.Logger
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Reusable object storage. It will store object [O] until last borrow
 */
interface ObjectStoragePickPoint<K, O> {
    /**
     * How many objects currently stored
     */
    val count: Int

    /**
     * Borrow object [O] by the [key]. Returned object should be returned using [ObjectBorrower.returnObject]
     * @param key key for borrowed object
     * @return new object borrower
     */
    suspend fun borrow(key: K): ObjectBorrower<O>
}

/**
 * Reusable object storage. It will store object [O] until last borrow
 */
interface SingleObjectStoragePickPoint<O> {
    /**
     * Does storage contain object
     */
    val empty: Boolean

    /**
     * Borrow object [O]. Returned object should be returned using [ObjectBorrower.returnObject]
     * @return new object borrower
     */
    suspend fun borrow(): ObjectBorrower<O>
}

internal interface ObjectStorageReturnPoint<O> {
    /**
     * Return previously borrowed object to the storage. You shouldn't use [obj] after that
     * If you call it twice with the same object nothing will happen
     * @param borrowerId unique id of the borrower
     * @param obj object to return
     */
    fun returnObject(borrowerId: String, obj: O)
}

/**
 * Closeable variant of [ObjectStoragePickPoint]
 */
internal interface CloseableObjectStorage {
    /**
     * Check is storage closed
     */
    val closed: Boolean

    /**
     * Close storage
     */
    fun close()
}

interface ObjectBorrower<O> {
    /**
     * True if object was returned
     */
    val returned: Boolean

    /**
     * Get underlying borrowed object.
     * It will throw [IllegalStateException] in case if object was returned.
     * It is undefined behavior if you will continue to use returned object after call [returnObject]
     */
    fun borrowedObject(): O

    /**
     * Return borrowed object. It will do nothing if object was alredy returned.
     * This object shouldn't be used anymore
     */
    fun returnObject()
}

/**
 * Default implementation which uses [actor] and [HashMap] as actual storage
 *
 * @param source objects source
 * @param releaseDelay delay before object release.
 * It will help to not destroy objects Immediately after some events like screen rotation.
 * So after new call of [borrow] you will receive same object as before
 * @param coroutineContext coroutine context
 *
 * @param K hashable key of the storage
 * @param O object to store
 */
internal class ObjectStorageImpl<K : Any, O>(
    private val source: Source<K, O>,
    private val releaseDelay: Long = 0L,
    coroutineContext: CoroutineContext = Dispatchers.IO
) : ObjectStoragePickPoint<K, O>, ObjectStorageReturnPoint<O>, CloseableObjectStorage {
    init {
        require(releaseDelay >= 0) { "Release delay should be positive" }
    }

    private val coroutineScope = CoroutineScope(coroutineContext)

    override val closed
        get() = !coroutineScope.isActive

    private val borrowReceipts: MutableMap<K, BorrowReceipt<O>> = hashMapOf()

    override val count: Int
        get() = borrowReceipts.size

    // All operations should goes through the owner
    private val objectsOwner = coroutineScope.actor<Action<K, O>> {
        fun BorrowReceipt<O>.acceptBorrow(): ObjectBorrower<O> {
            //Use UUID as unique borrower id
            val id = UUID.randomUUID().toString()

            borrowIds += id

            return DefaultObjectBorrower(id, obj, this@ObjectStorageImpl)
        }

        suspend fun borrowInner(key: K): ObjectBorrower<O> {
            Logger.debug("Object storage received new borrow action by key: $key")

            val borrower = borrowReceipts.getOrPut(key) {
                Logger.debug("Object storage has created new object by key: $key")

                BorrowReceipt(source.new(key))
            }.acceptBorrow()

            try {
                currentCoroutineContext().ensureActive()
            } catch (t: CancellationException) {
                // No one will receive that borrower. Return it
                borrower.returnObject()
                throw t
            }

            Logger.debug("Object storage add new object borrower by key: $key")

            return borrower
        }


        suspend fun returnObjectInner(borrowerId: String, obj: O) {
            val key = source.key(obj)

            Logger.debug("Object storage received new return object action by key: $key and id: $borrowerId")

            val receipt = borrowReceipts[key] ?: return

            check(receipt.borrowIds.remove(borrowerId)) { "Object user was already returned to the storage" }

            Logger.debug("Object storage has received borrowed object by key: $key. ${receipt.borrowIds.size} more borrows")

            // Remove object if there is no more borrows
            if (receipt.borrowIds.isEmpty()) {
                Logger.debug("Object storage trying to remove object by key: $key")
                source.onReleased(borrowReceipts.remove(key)!!.obj)
                Logger.debug("Object storage has removed object by key: $key")
            }
        }

        try {
            for (action in channel) {
                when (action) {
                    is Action.Borrow -> {
                        //will wait until child jobs completion
                        coroutineScope {
                            launch {
                                //Check complete status of borrow action
                                //Cancel this job as soon as borrow action cancelled
                                action.invokeOnCompletion {
                                    if (it != null) {
                                        if (it is CancellationException) {
                                            cancel(it)
                                        } else {
                                            cancel("Borrow job was completed with error", it)
                                        }
                                    }
                                }

                                try {
                                    val borrower = borrowInner(action.borrowKey)

                                    if (!action.complete(borrower)) {
                                        //nobody received that borrower, return it
                                        borrower.returnObject()
                                    }
                                } catch (t: Throwable) {
                                    if (t !is CancellationException) {
                                        action.completeExceptionally(t)
                                    }
                                }
                            }
                        }
                    }
                    is Action.Return -> returnObjectInner(action.borrowerId, action.obj)
                }
            }
        } finally {
            // clean objects
            withContext(NonCancellable) {
                borrowReceipts.values
                    .asFlow()
                    .collect { (obj, _) -> source.onReleased(obj) }

                borrowReceipts.clear()
            }
        }
    }

    override suspend fun borrow(key: K): ObjectBorrower<O> {
        check(coroutineScope.isActive) { "Can't borrow object. Storage closed" }

        return Action.Borrow<K, O>(key).also { objectsOwner.send(it) }.await()
    }


    override fun returnObject(borrowerId: String, obj: O) {
        coroutineScope.launch {
            if (releaseDelay > 0L) {
                delay(releaseDelay)
            }
            Action.Return<K, O>(borrowerId, obj).also { objectsOwner.send(it) }
        }
    }

    override fun close() {
        coroutineScope.cancel()
    }

    /**
     * Source of the object
     */
    interface Source<K, O> {
        /**
         * Create new object using provided key
         * @param key key to use during creation
         * @return created object
         */
        suspend fun new(key: K): O

        /**
         * Retrieve key from object
         * @param obj source object
         */
        fun key(obj: O): K

        /**
         * Called when object was fully released from any borrow and will be destroyed
         * @param obj object to destroy
         */
        suspend fun onReleased(obj: O) {
            //DO NOTHING
        }
    }

    /**
     * Describes which object was borrowed and ids of borrowers
     * @param obj object which was borrowed at least once
     * @param borrowIds borrowers ids
     */
    private data class BorrowReceipt<O>(val obj: O, val borrowIds: MutableSet<String> = hashSetOf())

    private class DefaultObjectBorrower<O>(
        private val id: String,
        obj: O,
        storage: ObjectStorageReturnPoint<O>
    ) : ObjectBorrower<O> {
        private val obj = WeakReference(obj)
        private val storage = WeakReference(storage)

        private val _returned = AtomicBoolean(false)

        override val returned: Boolean
            get() = _returned.get()

        override fun borrowedObject(): O {
            check(!returned) { "Borrowed object was returned. You can't use it anymore" }

            return tryGetObject()
        }

        override fun returnObject() {
            if (_returned.compareAndSet(false, true)) {
                val storage = checkNotNull(storage.get()) { "Storage was garbage collected" }

                storage.returnObject(id, tryGetObject())
            }
        }

        private fun tryGetObject() =
            checkNotNull(obj.get()) { "Borrowed object was garbage collected" }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DefaultObjectBorrower<*>

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun toString(): String {
            return "DefaultObjectBorrower(id='$id', obj=$obj, _returned=$_returned)"
        }
    }

    private sealed class Action<K, O> {
        class Borrow<K, O> private constructor(val borrowKey: K, parent: Job? = null) :
            Action<K, O>(),
            CompletableDeferred<ObjectBorrower<O>> by CompletableDeferred(parent) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Borrow<*, *>

                if (borrowKey != other.borrowKey) return false

                return true
            }

            override fun hashCode(): Int {
                return borrowKey?.hashCode() ?: 0
            }

            override fun toString(): String {
                return "Borrow(borrowKey=$borrowKey)"
            }

            companion object {
                /**
                 * Create new borrow action.
                 * Will set current coroutine context Job as parent for inner [CompletableDeferred]
                 * @param borrowKey key of borrow
                 * @return borrow action
                 */
                suspend operator fun <K, O> invoke(borrowKey: K) =
                    Borrow<K, O>(borrowKey, coroutineContext.job)
            }
        }

        data class Return<K, O>(val borrowerId: String, val obj: O) : Action<K, O>()
    }
}

/**
 * Special storage for objects with single possible variant
 * Technically it is a wrapper around [ObjectStorageImpl] with single possible [Key]
 *
 * @param storage inner storage
 * @param O object to store
 */
internal class SingleObjectStorageImpl<O> private constructor(private val storage: ObjectStorageImpl<Key, O>) :
    SingleObjectStoragePickPoint<O>,
    ObjectStorageReturnPoint<O> by storage,
    CloseableObjectStorage by storage {

    /**
     * @param source single variant source
     * @param releaseDelay delay before object release
     * @param coroutineContext coroutine context
     */
    constructor(
        source: Source<O>,
        releaseDelay: Long = 0L,
        coroutineContext: CoroutineContext = Dispatchers.IO
    ) : this(ObjectStorageImpl(object : ObjectStorageImpl.Source<Key, O> {
        override suspend fun new(key: Key) =
            source.new()

        override fun key(obj: O) = Key

        override suspend fun onReleased(obj: O) {
            super.onReleased(obj)
            source.onReleased(obj)
        }
    }, releaseDelay, coroutineContext))

    override val empty: Boolean
        get() = storage.count == 0

    override suspend fun borrow() =
        storage.borrow(Key)


    /**
     * Source of the object
     */
    interface Source<O> {
        /**
         * Create new object
         * @return created object
         */
        suspend fun new(): O

        /**
         * Called when object was fully released from any borrow and will be destroyed
         * @param obj object to destroy
         */
        suspend fun onReleased(obj: O) {
            //DO NOTHING
        }
    }

    private companion object Key
}

/**
 * Borrow object by [key] and invoke [body] with borrowed object.
 * Borrowed object will be automatically returned to storage
 */
suspend inline fun <K, O, R> ObjectStoragePickPoint<K, O>.withBorrow(key: K, body: (O) -> R) =
    borrow(key).use(body)

/**
 * Use underlying borrowed object and automatically return it to storage after usage
 */
inline fun <O, R> ObjectBorrower<O>.use(body: (O) -> R) =
    try {
        body(borrowedObject())
    } finally {
        returnObject()
    }

/**
 * Borrow object and invoke [body] with borrowed object.
 * Borrowed object will be automatically returned to storage
 */
suspend inline fun <O, R> SingleObjectStoragePickPoint<O>.withBorrow(body: (O) -> R) =
    borrow().use(body)