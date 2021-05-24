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

package app.seeneva.reader.logic.di

import androidx.lifecycle.Lifecycle
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.data.entity.ComicBook
import app.seeneva.reader.data.source.local.db.entity.FullComicBookWithTags
import app.seeneva.reader.data.source.local.db.entity.SimpleComicBookWithTags
import app.seeneva.reader.logic.ComicsSettings
import app.seeneva.reader.logic.PrefsComicsSettings
import app.seeneva.reader.logic.comic.Library
import app.seeneva.reader.logic.comic.LibraryFileManager
import app.seeneva.reader.logic.comic.LibraryFileManagerImpl
import app.seeneva.reader.logic.comic.LibraryImpl
import app.seeneva.reader.logic.entity.query.QueryParams
import app.seeneva.reader.logic.entity.query.QueryParamsResolver
import app.seeneva.reader.logic.entity.query.QueryParamsResolverImpl
import app.seeneva.reader.logic.entity.query.QueryParamsSerializer
import app.seeneva.reader.logic.entity.query.filter.FilterProvider
import app.seeneva.reader.logic.entity.query.filter.FilterProviderImpl
import app.seeneva.reader.logic.image.BitmapDiskCache
import app.seeneva.reader.logic.image.BitmapOkHttpDiskCache
import app.seeneva.reader.logic.image.ImageLoader
import app.seeneva.reader.logic.image.coil.CoilImageLoader
import app.seeneva.reader.logic.image.coil.fetcher.ComicImageFetcher
import app.seeneva.reader.logic.mapper.*
import app.seeneva.reader.logic.storage.*
import app.seeneva.reader.logic.text.SentenceBreakerFactory
import app.seeneva.reader.logic.text.ocr.OCR
import app.seeneva.reader.logic.text.ocr.TesseractOCR
import app.seeneva.reader.logic.text.tts.TTS
import app.seeneva.reader.logic.text.tts.TTSFactory
import app.seeneva.reader.logic.usecase.*
import app.seeneva.reader.logic.usecase.image.DecodePageUseCase
import app.seeneva.reader.logic.usecase.image.DecodePageUseCaseImpl
import app.seeneva.reader.logic.usecase.image.GetEncodedPageUseCase
import app.seeneva.reader.logic.usecase.image.GetEncodedPageUseCaseImpl
import app.seeneva.reader.logic.usecase.tags.ComicCompletedTagUseCase
import app.seeneva.reader.logic.usecase.tags.ComicCompletedTagUseCaseImpl
import app.seeneva.reader.logic.usecase.tags.ComicRemovedStateUseCaseImpl
import app.seeneva.reader.logic.usecase.tags.ComicRemovedTagUseCase
import app.seeneva.reader.logic.usecase.text.RecognizeTextUseCase
import app.seeneva.reader.logic.usecase.text.RecognizeTextUseCaseImpl
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import okhttp3.internal.cache.DiskLruCache
import okhttp3.internal.io.FileSystem
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module
import app.seeneva.reader.data.di.Modules as DataModules

object Modules {
    val all: Array<Module>
        get() = DataModules.all + arrayOf(
            logicModule,
            useCaseModule,
            mapperModule
        )

    private object Names {
        val mapperComicToListItem by lazy<Qualifier> { named("MAPPER_COMICS_TO_LIST_ITEM") }

        val mapperComicToInfo by lazy<Qualifier> { named("MAPPER_COMICS_TO_INFO") }

        val mapperComicToDescription by lazy<Qualifier> { named("MAPPER_COMICS_TO_DESCRIPTION") }

        val interpreterStorage by lazy<Qualifier> { named("INTERPRETER_STORAGE") }
    }

    private val logicModule = module {
        single<InterpreterObjectStorage>(Names.interpreterStorage) {
            SingleObjectStorageImpl(
                InterpreterSource(get()),
                // ML Interpreter is really heavy. So make it alive a little bit longer
                // It is rescue if you open multiple comic books
                // Maybe after refactoring of 'adding' logic it will not be necessary
                releaseDelay = 300,
                coroutineContext = get<Dispatchers>().io,
            )
        }

        single<EncodedComicPageStorage> {
            ObjectStorageImpl(
                EncodedImageSource(get()),
                coroutineContext = get<Dispatchers>().io
            )
        }

        single<Library> { LibraryImpl(inject(), inject(), inject()) }

        single<LibraryFileManager> { LibraryFileManagerImpl(androidApplication(), get()) }

        single<ComicsSettings> {
            // Add custom JSON serializer for QueryParams
            val settingsJson = Json {
                serializersModule += SerializersModule {
                    contextual(QueryParams::class, QueryParamsSerializer(get()))
                }
            }

            PrefsComicsSettings(androidApplication(), settingsJson, get())
        }

        single<FilterProvider> { FilterProviderImpl(androidApplication()) }

        single<QueryParamsResolver> { QueryParamsResolverImpl(get()) }

        single<BitmapDiskCache> {
            BitmapOkHttpDiskCache(
                DiskLruCache.create(
                    FileSystem.SYSTEM,
                    (androidApplication().externalCacheDir
                        ?: androidApplication().cacheDir).resolve("images"),
                    1,
                    1,
                    70 * 1024 * 1024
                ),
                inject(),
                get(),
            )
        }

        single {
            coil.ImageLoader.Builder(androidApplication()).componentRegistry {
                add(ComicImageFetcher(androidApplication(), get(), get(), get()))
            }.build()
        }

        single { get<coil.ImageLoader>().bitmapPool }

        factory<ImageLoader>(named<ImageLoader>()) { (lifecycle: Lifecycle) ->
            CoilImageLoader(
                androidApplication(),
                get(),
                get(),
                lifecycle,
            )
        }

        single { SentenceBreakerFactory(get<Dispatchers>().io) }
    }

    private val useCaseModule = module {
        single<FileDataUseCase> {
            FileDataUseCaseImpl(
                androidApplication(),
                get(),
                get()
            )
        }

        single<ComicRemovedTagUseCase> {
            ComicRemovedStateUseCaseImpl(
                get(),
                get(),
                get()
            )
        }

        single<RenameComicBookUseCase> { RenameComicBookUseCaseImpl(get(), get()) }

        single<GetComicInfoUseCase> {
            GetComicInfoUseCaseImpl(
                androidApplication(),
                get(),
                get(Names.mapperComicToInfo),
                get()
            )
        }

        single<ComicCompletedTagUseCase> {
            ComicCompletedTagUseCaseImpl(
                get(),
                get(),
                get()
            )
        }

        single<ComicListUseCase> {
            ComicListUseCaseImpl(
                get(),
                get(),
                get(),
                inject(),
                get(),
            )
        }

        single<ComicsPageUseCase> {
            ComicsPageUseCaseImpl(
                get(),
                get(),
                get(),
                get(),
                get(Names.mapperComicToListItem),
                get(),
            )
        }

        single<AddingUseCase> {
            AddingUseCaseImpl(
                get(Names.interpreterStorage),
                get(),
                get(),
                get(),
                get(),
                get(),
                get()
            )
        }

        single<DeleteBookByIdUseCase> { DeleteBookByIdUseCaseImpl(get(), get()) }

        single<SyncUseCase> {
            SyncUseCaseImpl(
                androidApplication(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get()
            )
        }

        single<GetEncodedPageUseCase> { GetEncodedPageUseCaseImpl(get()) }

        single<DecodePageUseCase> { DecodePageUseCaseImpl(get()) }

        single<BookViewerUseCase> {
            BookViewerUseCaseImpl(
                get(),
                get(),
                get(),
                get(),
                get(Names.mapperComicToDescription),
                get()
            )
        }

        single<ViewerConfigUseCase> { ViewerConfigUseCaseImpl(get()) }

        single<InterpreterUseCase> { InterpreterUseCaseImpl(get()) }

        single<GetPageDataUseCase> { GetPageDataUseCaseImpl(get(), get(), get(), get()) }

        single<RecognizeTextUseCase> { RecognizeTextUseCaseImpl(get(), get()) }

        single<ThirdPartyUseCase> { ThirdPartyUseCaseImpl(androidApplication(), get()) }

        single<OCR.Factory> { TesseractOCR.Factory(get(), get()) }
        single<TTS.Factory> { TTSFactory(androidApplication(), get(), inject()) }
    }

    private val mapperModule = module {
        factory<ComicMetadataIntoComicListItem>(Names.mapperComicToListItem) { SimpleComicBookWithTags::intoListItem }
        factory<ComicMetadataIntoComicInfo>(Names.mapperComicToInfo) { FullComicBookWithTags?::intoComicInfo }
        factory<ComicBookIntoDescription>(Names.mapperComicToDescription) { ComicBook?::intoDescription }
    }
}