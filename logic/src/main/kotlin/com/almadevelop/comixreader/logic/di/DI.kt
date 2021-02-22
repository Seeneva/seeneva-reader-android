package com.almadevelop.comixreader.logic.di

import androidx.lifecycle.Lifecycle
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.data.entity.ComicBook
import com.almadevelop.comixreader.data.source.local.db.entity.FullComicBookWithTags
import com.almadevelop.comixreader.data.source.local.db.entity.SimpleComicBookWithTags
import com.almadevelop.comixreader.logic.ComicsPagingDataSource
import com.almadevelop.comixreader.logic.ComicsPagingDataSourceFactory
import com.almadevelop.comixreader.logic.ComicsSettings
import com.almadevelop.comixreader.logic.PrefsComicsSettings
import com.almadevelop.comixreader.logic.comic.Library
import com.almadevelop.comixreader.logic.comic.LibraryFileManager
import com.almadevelop.comixreader.logic.comic.LibraryFileManagerImpl
import com.almadevelop.comixreader.logic.comic.LibraryImpl
import com.almadevelop.comixreader.logic.entity.query.QueryParamsResolver
import com.almadevelop.comixreader.logic.entity.query.QueryParamsResolverImpl
import com.almadevelop.comixreader.logic.entity.query.filter.FilterProvider
import com.almadevelop.comixreader.logic.entity.query.filter.FilterProviderImpl
import com.almadevelop.comixreader.logic.image.BitmapDiskCache
import com.almadevelop.comixreader.logic.image.BitmapOkHttpDiskCache
import com.almadevelop.comixreader.logic.image.ImageLoader
import com.almadevelop.comixreader.logic.image.coil.CoilImageLoader
import com.almadevelop.comixreader.logic.image.coil.fetcher.ComicImageFetcher
import com.almadevelop.comixreader.logic.mapper.*
import com.almadevelop.comixreader.logic.storage.*
import com.almadevelop.comixreader.logic.text.SentenceBreakerFactory
import com.almadevelop.comixreader.logic.text.ocr.OCR
import com.almadevelop.comixreader.logic.text.ocr.TesseractOCR
import com.almadevelop.comixreader.logic.text.tts.TTS
import com.almadevelop.comixreader.logic.text.tts.TTSFactory
import com.almadevelop.comixreader.logic.usecase.*
import com.almadevelop.comixreader.logic.usecase.image.DecodePageUseCase
import com.almadevelop.comixreader.logic.usecase.image.DecodePageUseCaseImpl
import com.almadevelop.comixreader.logic.usecase.image.GetEncodedPageUseCase
import com.almadevelop.comixreader.logic.usecase.image.GetEncodedPageUseCaseImpl
import com.almadevelop.comixreader.logic.usecase.tags.ComicCompletedTagUseCase
import com.almadevelop.comixreader.logic.usecase.tags.ComicCompletedTagUseCaseImpl
import com.almadevelop.comixreader.logic.usecase.tags.ComicRemovedStateUseCaseImpl
import com.almadevelop.comixreader.logic.usecase.tags.ComicRemovedTagUseCase
import com.almadevelop.comixreader.logic.usecase.text.RecognizeTextUseCase
import com.almadevelop.comixreader.logic.usecase.text.RecognizeTextUseCaseImpl
import kotlinx.coroutines.Job
import okhttp3.internal.cache.DiskLruCache
import okhttp3.internal.io.FileSystem
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module
import com.almadevelop.comixreader.data.di.Modules as DataModules

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
                releaseDelay = 300
            )
        }

        single<EncodedComicPageStorage> { ObjectStorageImpl(EncodedImageSource(get())) }

        single<Library> { LibraryImpl(inject(), inject(), inject()) }

        single<LibraryFileManager> { LibraryFileManagerImpl(androidApplication(), get()) }

        single<ComicsSettings> { PrefsComicsSettings(androidApplication(), get(), get()) }

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

        factory<ComicsPagingDataSourceFactory> { (parentJob: Job?) ->
            ComicsPagingDataSource.Factory(
                get(),
                get(),
                parentJob
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
                get(),
                get(),
                get(Names.mapperComicToListItem)
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

        single<OCR.Factory> { TesseractOCR.Factory(get(), get()) }
        single<TTS.Factory> { TTSFactory(androidApplication(), get(), inject()) }
    }

    private val mapperModule = module {
        factory<ComicMetadataIntoComicListItem>(Names.mapperComicToListItem) { SimpleComicBookWithTags::intoListItem }
        factory<ComicMetadataIntoComicInfo>(Names.mapperComicToInfo) { FullComicBookWithTags?::intoComicInfo }
        factory<ComicBookIntoDescription>(Names.mapperComicToDescription) { ComicBook?::intoDescription }
    }
}