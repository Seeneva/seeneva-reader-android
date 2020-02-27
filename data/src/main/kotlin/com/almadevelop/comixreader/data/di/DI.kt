package com.almadevelop.comixreader.data.di

import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.data.source.jni.NativeSource
import com.almadevelop.comixreader.data.source.jni.NativeSourceImpl
import com.almadevelop.comixreader.data.source.local.db.ComicDatabase
import com.almadevelop.comixreader.data.source.local.db.LocalTransactionRunner
import com.almadevelop.comixreader.data.source.local.db.LocalTransactionRunnerImpl
import kotlinx.coroutines.asExecutor
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.dsl.module

object Modules {
    val all: Array<Module>
        get() = arrayOf(roomModule, sourceModule)

    private val roomModule = module {
        single { ComicDatabase.instance(androidApplication(), get<Dispatchers>().io.asExecutor()) }

        single<LocalTransactionRunner> { LocalTransactionRunnerImpl(get()) }
    }

    private val sourceModule = module {
        single<NativeSource> { NativeSourceImpl(androidApplication(), get<Dispatchers>().io) }

        single { get<ComicDatabase>().comicBookSource() }
        single { get<ComicDatabase>().comicTagSource() }
    }
}