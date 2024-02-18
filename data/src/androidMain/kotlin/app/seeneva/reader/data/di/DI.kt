/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
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

package app.seeneva.reader.data.di

import androidx.annotation.VisibleForTesting
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.data.source.jni.NativeSource
import app.seeneva.reader.data.source.jni.NativeSourceImpl
import app.seeneva.reader.data.source.local.db.ComicDatabase
import app.seeneva.reader.data.source.local.db.LocalTransactionRunner
import app.seeneva.reader.data.source.local.db.LocalTransactionRunnerImpl
import kotlinx.coroutines.asExecutor
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.dsl.module

object Modules {
    val all: Array<Module>
        get() = arrayOf(roomModule, sourceModule, nativeModule)

    private val roomModule = module {
        single { ComicDatabase.instance(androidApplication(), get<Dispatchers>().io.asExecutor()) }

        single<LocalTransactionRunner> { LocalTransactionRunnerImpl(get()) }
    }

    private val sourceModule = module {
        single { get<ComicDatabase>().comicBookSource() }
        single { get<ComicDatabase>().comicTagSource() }
        single { get<ComicDatabase>().comicBookPageSource() }
        single { get<ComicDatabase>().comicBookPageObjectSource() }
    }

    @VisibleForTesting
    val nativeModule = module {
        single<NativeSource> { NativeSourceImpl(androidApplication(), get<Dispatchers>().io) }
    }
}