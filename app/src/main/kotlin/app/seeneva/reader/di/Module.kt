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

package app.seeneva.reader.di

import android.app.Application
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkManager
import app.seeneva.reader.AppDispatchers
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.logic.image.ImageLoader
import app.seeneva.reader.logic.text.ocr.OCR
import app.seeneva.reader.logic.text.tts.TTS
import app.seeneva.reader.logic.text.tts.newViewerTTS
import app.seeneva.reader.router.asRouterContext
import app.seeneva.reader.screen.about.licenses.*
import app.seeneva.reader.screen.list.*
import app.seeneva.reader.screen.list.dialog.filters.*
import app.seeneva.reader.screen.list.dialog.info.*
import app.seeneva.reader.screen.list.dialog.info.ComicInfoFragment.Companion.bookId
import app.seeneva.reader.screen.viewer.*
import app.seeneva.reader.screen.viewer.BookViewerActivity.Companion.bookId
import app.seeneva.reader.screen.viewer.dialog.config.*
import app.seeneva.reader.screen.viewer.page.*
import app.seeneva.reader.screen.viewer.page.BookViewerPageFragment.Companion.pageId
import app.seeneva.reader.service.add.*
import app.seeneva.reader.work.SyncManager
import app.seeneva.reader.work.SyncWorkManager
import app.seeneva.reader.work.worker.SyncWorker
import kotlinx.coroutines.Job
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.KoinApplication
import org.koin.core.module.dsl.*
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.core.qualifier.qualifier
import org.koin.dsl.ScopeDSL
import org.koin.dsl.module
import org.koin.dsl.onClose
import app.seeneva.reader.logic.di.Module as LogicModule

enum class Name {
    VIEWER_RETAIN_SCOPE
}

object Module {
    private val app = module {
        single<Dispatchers> { AppDispatchers }

        single<SyncManager> { SyncWorkManager(WorkManager.getInstance(androidApplication())) }

        worker { SyncWorker(get(), get(), inject()) }

        factory<AddComicBookServiceConnector> { (parent: Job) ->
            AddComicBookServiceConnectorImpl(
                androidApplication(),
                parent,
                get()
            )
        }
    }

    private val components = module {
        scope<ComicsListFragment> {
            scopedOf(::ComicsListPresenterImpl) withOptions {
                bind<ComicsListPresenter>()
            }

            scoped<ComicListRouter> {
                val src = get<Fragment>()

                ComicListRouterImpl(src.asRouterContext(), src)
            }

            scopedImageLoader()
        }

        scope<ComicInfoFragment> {
            scoped<ComicInfoPresenter> {
                val fragment = get<ComicInfoFragment>()

                ComicInfoPresenterImpl(
                    fragment,
                    get(),
                    get(),
                    fragment.bookId
                )
            }
        }

        scope(Name.VIEWER_RETAIN_SCOPE.qualifier) {
            scoped { get<OCR.Factory>().new() }.onClose { it?.close() }

            scoped { get<TTS.Factory>().newViewerTTS() }.onClose { it?.close() }
        }

        scope<BookViewerActivity> {
            scopedImageLoader()

            scoped<BookViewerPresenter> {
                val activity = get<BookViewerActivity>()

                BookViewerPresenterImpl(
                    activity,
                    get(),
                    get(),
                    activity.bookId
                )
            }
        }

        scope<BookViewerPageFragment> {
            scopedImageLoader()

            scoped<BookViewerPagePresenter> {
                val fragment = get<BookViewerPageFragment>()

                BookViewerPagePresenterImpl(
                    fragment,
                    get(),
                    get(),
                    get(),
                    inject(),
                    get(),
                    get(),
                    fragment.pageId
                )
            }
        }

        scope<ViewerConfigDialog> {
            scoped<ViewerConfigPresenter> {
                ViewerConfigPresenterImpl(
                    get(),
                    get(),
                    inject(),
                    get()
                )
            }

            scoped { get<TTS.Factory>().newResolver() }
        }

        scope<ThirdPartyActivity> {
            scopedOf(::ThirdPartyPresenterImpl) withOptions {
                bind<ThirdPartyPresenter>()
            }
        }

        scope<EditFiltersDialog> {
            scoped<EditFiltersPresenter> {
                val fragment = get<EditFiltersDialog>()

                EditFiltersPresenterImpl(
                    fragment,
                    get(),
                    EditFiltersDialog.readSelectedFilters(fragment),
                    get()
                )
            }
        }

        scope<AddComicBookService> {
            scoped<AddComicBookPresenter> {
                AddComicBookPresenterImpl(
                    get<AddComicBookService>(),
                    get(),
                    get(),
                    get(),
                )
            }
        }

        viewModel {
            //job provided to the pagination factory as parent
            //So, when viewModel's job cancelled child will cancel too
            val job = Job()
            ComicsListViewModelImpl(
                get(),
                get(),
                get { parametersOf(job) },
                get(),
                get(),
                get(),
                get(),
                get(),
                job
            )
        } withOptions {
            bind<ComicsListViewModel>()
        }

        viewModelOf(::ComicInfoViewModelImpl) withOptions {
            bind<ComicInfoViewModel>()
        }

        viewModelOf(::EditFiltersViewModelImpl) withOptions {
            bind<EditFiltersViewModel>()
        }

        viewModelOf(::BookViewerViewModelImpl) withOptions {
            bind<BookViewerViewModel>()
        }

        viewModel { BookViewerPageViewModelImpl(get(), inject(), get(), get()) } withOptions {
            bind<BookViewerPageViewModel>()
        }

        viewModelOf(::ViewerConfigViewModelImpl) withOptions {
            bind<ViewerConfigViewModel>()
        }

        viewModelOf(::ThirdPartyViewModelImpl) withOptions {
            bind<ThirdPartyViewModel>()
        }
    }

    // https://github.com/InsertKoinIO/koin/issues/1702
    /**
     * Main DI module for the app
     */
    val main = module {
        includes(
            LogicModule.main,
            app,
            components
        )
    }

    /**
     * Get image loader using current scope source which sould implement [LifecycleOwner]
     */
    private fun ScopeDSL.scopedImageLoader() {
        scoped<ImageLoader> {
            get(named<ImageLoader>()) { parametersOf(get<LifecycleOwner>().lifecycle) }
        }
    }
}

fun KoinApplication.setup(app: Application) {
    androidContext(app)
    workManagerFactory()
    modules(Module.main)
}