package com.almadevelop.comixreader.logic.di

import com.almadevelop.comixreader.data.source.local.db.entity.FullComicBookWithTags
import com.almadevelop.comixreader.data.source.local.db.entity.SimpleComicBookWithTags
import com.almadevelop.comixreader.logic.*
import com.almadevelop.comixreader.logic.comic.Library
import com.almadevelop.comixreader.logic.comic.LibraryFileManager
import com.almadevelop.comixreader.logic.comic.LibraryFileManagerImpl
import com.almadevelop.comixreader.logic.comic.LibraryImpl
import com.almadevelop.comixreader.logic.entity.query.QueryParamsResolver
import com.almadevelop.comixreader.logic.entity.query.QueryParamsResolverImpl
import com.almadevelop.comixreader.logic.entity.query.filter.FilterProvider
import com.almadevelop.comixreader.logic.entity.query.filter.FilterProviderImpl
import com.almadevelop.comixreader.logic.glide.GlideImageLoader
import com.almadevelop.comixreader.logic.mapper.ComicMetadataIntoComicInfo
import com.almadevelop.comixreader.logic.mapper.ComicMetadataIntoComicListItem
import com.almadevelop.comixreader.logic.mapper.intoComicInfo
import com.almadevelop.comixreader.logic.mapper.intoListItem
import com.almadevelop.comixreader.logic.usecase.*
import com.almadevelop.comixreader.logic.usecase.tags.ComicCompletedTagUseCase
import com.almadevelop.comixreader.logic.usecase.tags.ComicCompletedTagUseCaseImpl
import com.almadevelop.comixreader.logic.usecase.tags.ComicRemovedStateUseCaseImpl
import com.almadevelop.comixreader.logic.usecase.tags.ComicRemovedTagUseCase
import kotlinx.coroutines.Job
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.core.scope.ScopeID
import org.koin.dsl.module
import com.almadevelop.comixreader.data.di.Modules as DataModules

object ScopeId {
    const val GLIDE: ScopeID = "glide"
}

object Modules {
    val all: Array<Module>
        get() = DataModules.all + arrayOf(
            logicModule,
            useCaseModule,
            mapperModule
        )

    private object Names {
        val mapperComicToListItemName: Qualifier
            get() = named("MAPPER_COMICS_TO_LIST_ITEM")

        val mapperComicToInfoName: Qualifier
            get() = named("MAPPER_COMICS_TO_INFO")
    }

    private val logicModule = module {
        single<Library> { LibraryImpl(inject(), inject(), inject()) }

        single<LibraryFileManager> { LibraryFileManagerImpl(androidApplication(), get()) }

        single<ComicsSettings> { PrefsComicsSettings(androidApplication(), get()) }

        single<FilterProvider> { FilterProviderImpl(androidApplication()) }

        single<QueryParamsResolver> { QueryParamsResolverImpl(get()) }

        factory<ComicsPagingDataSourceFactory> { (parentJob: Job?) ->
            ComicsPagingDataSource.Factory(
                get(),
                get(),
                parentJob
            )
        }

        factory<ImageLoader> { GlideImageLoader(getScope(ScopeId.GLIDE).get()) }
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
                get(Names.mapperComicToInfoName),
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
                get(Names.mapperComicToListItemName)
            )
        }

        single<AddingUseCase> { AddingUseCaseImpl(get(), get(), get(), get(), get(), get()) }

        single<DeleteBookByIdUseCase> { DeleteBookByIdUseCaseImpl(get(), get()) }

        single<SyncUseCase> {
            SyncUseCaseImpl(
                androidApplication(),
                get(),
                get(),
                get(),
                get(),
                get()
            )
        }
    }

    private val mapperModule = module {
        factory<ComicMetadataIntoComicListItem>(Names.mapperComicToListItemName) { SimpleComicBookWithTags::intoListItem }
        factory<ComicMetadataIntoComicInfo>(Names.mapperComicToInfoName) { FullComicBookWithTags?::intoComicInfo }
    }
}