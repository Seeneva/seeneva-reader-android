package com.almadevelop.comixreader.di

import android.app.Application
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkManager
import com.almadevelop.comixreader.AppDispatchers
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.logic.image.ImageLoader
import com.almadevelop.comixreader.logic.text.ocr.OCR
import com.almadevelop.comixreader.logic.text.tts.TTS
import com.almadevelop.comixreader.logic.text.tts.newViewerTTS
import com.almadevelop.comixreader.router.asRouterContext
import com.almadevelop.comixreader.screen.list.*
import com.almadevelop.comixreader.screen.list.dialog.filters.EditFiltersDialog
import com.almadevelop.comixreader.screen.list.dialog.filters.EditFiltersPresenter
import com.almadevelop.comixreader.screen.list.dialog.filters.EditFiltersPresenterImpl
import com.almadevelop.comixreader.screen.list.dialog.filters.EditFiltersViewModelImpl
import com.almadevelop.comixreader.screen.list.dialog.info.ComicInfoFragment
import com.almadevelop.comixreader.screen.list.dialog.info.ComicInfoFragment.Companion.bookId
import com.almadevelop.comixreader.screen.list.dialog.info.ComicInfoPresenter
import com.almadevelop.comixreader.screen.list.dialog.info.ComicInfoPresenterImpl
import com.almadevelop.comixreader.screen.list.dialog.info.ComicInfoViewModelImpl
import com.almadevelop.comixreader.screen.viewer.BookViewerActivity
import com.almadevelop.comixreader.screen.viewer.BookViewerActivity.Companion.bookId
import com.almadevelop.comixreader.screen.viewer.BookViewerPresenter
import com.almadevelop.comixreader.screen.viewer.BookViewerPresenterImpl
import com.almadevelop.comixreader.screen.viewer.BookViewerViewModelImpl
import com.almadevelop.comixreader.screen.viewer.dialog.config.ViewerConfigDialog
import com.almadevelop.comixreader.screen.viewer.dialog.config.ViewerConfigPresenter
import com.almadevelop.comixreader.screen.viewer.dialog.config.ViewerConfigPresenterImpl
import com.almadevelop.comixreader.screen.viewer.dialog.config.ViewerConfigViewModelImpl
import com.almadevelop.comixreader.screen.viewer.page.BookViewerPageFragment
import com.almadevelop.comixreader.screen.viewer.page.BookViewerPageFragment.Companion.pageId
import com.almadevelop.comixreader.screen.viewer.page.BookViewerPagePresenter
import com.almadevelop.comixreader.screen.viewer.page.BookViewerPagePresenterImpl
import com.almadevelop.comixreader.screen.viewer.page.BookViewerPageViewModelImpl
import com.almadevelop.comixreader.service.add.*
import com.almadevelop.comixreader.work.SyncManager
import com.almadevelop.comixreader.work.SyncWorkManager
import com.almadevelop.comixreader.work.worker.SyncWorker
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
import com.almadevelop.comixreader.logic.di.Modules as LogicModules

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

            scoped<ComicListRouter> { ComicListRouterImpl(getSource<Fragment>().asRouterContext()) }

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
                get { parametersOf(job as Job) },
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