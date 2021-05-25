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
import app.seeneva.reader.screen.about.licenses.ThirdPartyActivity
import app.seeneva.reader.screen.about.licenses.ThirdPartyPresenter
import app.seeneva.reader.screen.about.licenses.ThirdPartyPresenterImpl
import app.seeneva.reader.screen.about.licenses.ThirdPartyViewModelImpl
import app.seeneva.reader.screen.list.*
import app.seeneva.reader.screen.list.dialog.filters.EditFiltersDialog
import app.seeneva.reader.screen.list.dialog.filters.EditFiltersPresenter
import app.seeneva.reader.screen.list.dialog.filters.EditFiltersPresenterImpl
import app.seeneva.reader.screen.list.dialog.filters.EditFiltersViewModelImpl
import app.seeneva.reader.screen.list.dialog.info.ComicInfoFragment
import app.seeneva.reader.screen.list.dialog.info.ComicInfoFragment.Companion.bookId
import app.seeneva.reader.screen.list.dialog.info.ComicInfoPresenter
import app.seeneva.reader.screen.list.dialog.info.ComicInfoPresenterImpl
import app.seeneva.reader.screen.list.dialog.info.ComicInfoViewModelImpl
import app.seeneva.reader.screen.viewer.BookViewerActivity
import app.seeneva.reader.screen.viewer.BookViewerActivity.Companion.bookId
import app.seeneva.reader.screen.viewer.BookViewerPresenter
import app.seeneva.reader.screen.viewer.BookViewerPresenterImpl
import app.seeneva.reader.screen.viewer.BookViewerViewModelImpl
import app.seeneva.reader.screen.viewer.dialog.config.ViewerConfigDialog
import app.seeneva.reader.screen.viewer.dialog.config.ViewerConfigPresenter
import app.seeneva.reader.screen.viewer.dialog.config.ViewerConfigPresenterImpl
import app.seeneva.reader.screen.viewer.dialog.config.ViewerConfigViewModelImpl
import app.seeneva.reader.screen.viewer.page.BookViewerPageFragment
import app.seeneva.reader.screen.viewer.page.BookViewerPageFragment.Companion.pageId
import app.seeneva.reader.screen.viewer.page.BookViewerPagePresenter
import app.seeneva.reader.screen.viewer.page.BookViewerPagePresenterImpl
import app.seeneva.reader.screen.viewer.page.BookViewerPageViewModelImpl
import app.seeneva.reader.service.add.*
import app.seeneva.reader.work.SyncManager
import app.seeneva.reader.work.SyncWorkManager
import app.seeneva.reader.work.worker.SyncWorker
import kotlinx.coroutines.Job
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.scope.ScopeHandlerViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.workmanager.dsl.worker
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.ScopeDSL
import org.koin.dsl.module
import org.koin.dsl.onClose
import app.seeneva.reader.logic.di.Modules as LogicModules

object Names {
    val viewerRetainScope: Qualifier
        get() = named("VIEWER_RETAIN_SCOPE")
}

private object Modules {
    val all: Array<Module>
        get() = LogicModules.all +
                arrayOf(
                    appModule,
                    componentsModule,
                )

    private val appModule = module {
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

        viewModel { ScopeHandlerViewModel() }
    }

    private val componentsModule = module {
        scope<ComicsListFragment> {
            scoped<ComicsListPresenter> {
                val fragment = getSource<ComicsListFragment>()

                ComicsListPresenterImpl(
                    fragment,
                    get(),
                    get(),
                    fragment.getViewModel<ComicsListViewModelImpl>()
                )
            }

            scoped<ComicListRouter> {
                val src = getSource<Fragment>()

                ComicListRouterImpl(src.asRouterContext(), src)
            }

            scopedImageLoader()
        }

        scope<ComicInfoFragment> {
            scoped<ComicInfoPresenter> {
                val fragment = getSource<ComicInfoFragment>()

                ComicInfoPresenterImpl(
                    fragment,
                    get(),
                    fragment.getViewModel<ComicInfoViewModelImpl>(),
                    fragment.bookId
                )
            }
        }

        scope(Names.viewerRetainScope) {
            scoped { get<OCR.Factory>().new() }.onClose { it?.close() }

            scoped { get<TTS.Factory>().newViewerTTS() }.onClose { it?.close() }
        }

        scope<BookViewerActivity> {
            scopedImageLoader()

            scoped<BookViewerPresenter> {
                val activity = getSource<BookViewerActivity>()

                BookViewerPresenterImpl(
                    activity,
                    get(),
                    activity.getViewModel<BookViewerViewModelImpl>(),
                    activity.bookId
                )
            }
        }

        scope<BookViewerPageFragment> {
            scopedImageLoader()

            scoped<BookViewerPagePresenter> {
                val fragment = getSource<BookViewerPageFragment>()

                BookViewerPagePresenterImpl(
                    fragment,
                    get(),
                    get(),
                    get(),
                    inject(),
                    get(),
                    fragment.getViewModel<BookViewerPageViewModelImpl>(),
                    fragment.pageId
                )
            }
        }

        scope<ViewerConfigDialog> {
            scoped<ViewerConfigPresenter> {
                val fragment = getSource<ViewerConfigDialog>()

                ViewerConfigPresenterImpl(
                    fragment,
                    get(),
                    inject(),
                    fragment.getViewModel<ViewerConfigViewModelImpl>()
                )
            }

            scoped { get<TTS.Factory>().newResolver() }
        }

        scope<ThirdPartyActivity> {
            scoped<ThirdPartyPresenter> {
                val fragment = getSource<ThirdPartyActivity>()

                ThirdPartyPresenterImpl(
                    fragment,
                    get(),
                    fragment.getViewModel<ThirdPartyViewModelImpl>()
                )
            }
        }

        scope<EditFiltersDialog> {
            scoped<EditFiltersPresenter> {
                val fragment = getSource<EditFiltersDialog>()

                EditFiltersPresenterImpl(
                    fragment,
                    get(),
                    EditFiltersDialog.readSelectedFilters(fragment),
                    fragment.getViewModel<EditFiltersViewModelImpl>()
                )
            }
        }

        scope<AddComicBookService> {
            scoped<AddComicBookPresenter> {
                AddComicBookPresenterImpl(
                    getSource<AddComicBookService>(),
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
        }

        viewModel { ComicInfoViewModelImpl(get(), get()) }

        viewModel { EditFiltersViewModelImpl(get(), get()) }

        viewModel { BookViewerViewModelImpl(get(), get(), get()) }

        viewModel { BookViewerPageViewModelImpl(get(), inject(), get(), get()) }

        viewModel { ViewerConfigViewModelImpl(androidApplication(), get(), get()) }

        viewModel { ThirdPartyViewModelImpl(get(), get()) }
    }
}

fun KoinApplication.setup(app: Application) {
    androidContext(app)
    workManagerFactory()
    modules(Modules.all.asList())
}

/**
 * Get image loader using current scope source which sould implement [LifecycleOwner]
 */
private fun ScopeDSL.scopedImageLoader() {
    scoped<ImageLoader> {
        get(named<ImageLoader>()) { parametersOf(getSource<LifecycleOwner>().lifecycle) }
    }
}