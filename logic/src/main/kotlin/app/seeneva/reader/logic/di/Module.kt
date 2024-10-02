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
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module
import app.seeneva.reader.data.di.Module as DataModule

private enum class Name {
    MAPPER_COMICS_TO_LIST_ITEM,
    MAPPER_COMICS_TO_INFO,
    MAPPER_COMICS_TO_DESCRIPTION,
    INTERPRETER_STORAGE
}

object Module {
    private val logic = module {
        single<InterpreterObjectStorage>(Name.INTERPRETER_STORAGE.qualifier) {
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

        singleOf(::LibraryFileManagerImpl) withOptions {
            bind<LibraryFileManager>()
        }

        single<ComicsSettings> {
            // Add custom JSON serializer for QueryParams
            val settingsJson = Json {
                serializersModule += SerializersModule {
                    contextual(QueryParams::class, QueryParamsSerializer(get()))
                }
            }

            PrefsComicsSettings(androidApplication(), settingsJson, get())
        }

        singleOf(::FilterProviderImpl) withOptions {
            bind<FilterProvider>()
        }

        singleOf(::QueryParamsResolverImpl) withOptions {
            bind<QueryParamsResolver>()
        }

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

    private val useCase = module {
        singleOf(::FileDataUseCaseImpl) withOptions {
            bind<FileDataUseCase>()
        }

        singleOf(::ComicRemovedStateUseCaseImpl) withOptions {
            bind<ComicRemovedTagUseCase>()
        }

        singleOf(::RenameComicBookUseCaseImpl) withOptions {
            bind<RenameComicBookUseCase>()
        }

        single<GetComicInfoUseCase> {
            GetComicInfoUseCaseImpl(
                androidApplication(),
                get(),
                get(Name.MAPPER_COMICS_TO_INFO.qualifier),
                get()
            )
        }

        singleOf(::ComicCompletedTagUseCaseImpl) withOptions {
            bind<ComicCompletedTagUseCase>()
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
                get(Name.MAPPER_COMICS_TO_LIST_ITEM.qualifier),
                get(),
            )
        }

        single<AddingUseCase> {
            AddingUseCaseImpl(
                get(Name.INTERPRETER_STORAGE.qualifier),
                get(),
                get(),
                get(),
                get(),
                get(),
                get()
            )
        }

        singleOf(::DeleteBookByIdUseCaseImpl) withOptions {
            bind<DeleteBookByIdUseCase>()
        }

        singleOf(::SyncUseCaseImpl) withOptions {
            bind<SyncUseCase>()
        }

        singleOf(::GetEncodedPageUseCaseImpl) withOptions {
            bind<GetEncodedPageUseCase>()
        }

        singleOf(::DecodePageUseCaseImpl) withOptions {
            bind<DecodePageUseCase>()
        }

        single<BookViewerUseCase> {
            BookViewerUseCaseImpl(
                get(),
                get(),
                get(),
                get(),
                get(Name.MAPPER_COMICS_TO_DESCRIPTION.qualifier),
                get()
            )
        }

        singleOf(::ViewerConfigUseCaseImpl) withOptions {
            bind<ViewerConfigUseCase>()
        }

        singleOf(::InterpreterUseCaseImpl) withOptions {
            bind<InterpreterUseCase>()
        }

        singleOf(::GetPageDataUseCaseImpl) withOptions {
            bind<GetPageDataUseCase>()
        }

        singleOf(::RecognizeTextUseCaseImpl) withOptions {
            bind<RecognizeTextUseCase>()
        }

        single<ThirdPartyUseCase> { ThirdPartyUseCaseImpl(androidApplication(), get()) }

        singleOf(TesseractOCR::Factory) withOptions {
            bind<OCR.Factory>()
        }
        single<TTS.Factory> { TTSFactory(androidApplication(), get(), inject()) }
    }

    private val mapper = module {
        factory<ComicMetadataIntoComicListItem>(Name.MAPPER_COMICS_TO_LIST_ITEM.qualifier) { SimpleComicBookWithTags::intoListItem }
        factory<ComicMetadataIntoComicInfo>(Name.MAPPER_COMICS_TO_INFO.qualifier) { FullComicBookWithTags?::intoComicInfo }
        factory<ComicBookIntoDescription>(Name.MAPPER_COMICS_TO_DESCRIPTION.qualifier) { ComicBook?::intoDescription }
    }

    /**
     * Main module of the library
     */
    val main = module {
        includes(
            DataModule.main,
            logic,
            useCase,
            mapper
        )
    }
}