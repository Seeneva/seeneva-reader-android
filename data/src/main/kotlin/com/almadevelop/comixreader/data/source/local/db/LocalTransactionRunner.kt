package com.almadevelop.comixreader.data.source.local.db

import androidx.room.withTransaction

interface LocalTransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R
}

internal class LocalTransactionRunnerImpl(
    private val database: ComicDatabase
) : LocalTransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R =
        database.withTransaction(block)
}