package com.almadevelop.comixreader.di

import android.app.Application
import android.content.ComponentCallbacks
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkManager
import com.almadevelop.comixreader.AppDispatchers
import com.almadevelop.comixreader.GlideApp
import com.almadevelop.comixreader.common.coroutines.Dispatchers
import com.almadevelop.comixreader.screen.list.ComicsListFragment
import com.almadevelop.comixreader.screen.list.ComicsListPresenter
import com.almadevelop.comixreader.screen.list.ComicsListPresenterImpl
import com.almadevelop.comixreader.screen.list.ComicsViewModelImpl
import com.almadevelop.comixreader.screen.list.dialog.AddModeSelectorDialog
import com.almadevelop.comixreader.screen.list.dialog.ComicRenameDialog
import com.almadevelop.comixreader.screen.list.dialog.ComicsSortDialog
import com.almadevelop.comixreader.screen.list.dialog.filters.EditFiltersDialog
import com.almadevelop.comixreader.screen.list.dialog.filters.EditFiltersPresenter
import com.almadevelop.comixreader.screen.list.dialog.filters.EditFiltersPresenterImpl
import com.almadevelop.comixreader.screen.list.dialog.filters.EditFiltersViewModelImpl
import com.almadevelop.comixreader.screen.list.dialog.info.ComicInfoFragment
import com.almadevelop.comixreader.screen.list.dialog.info.ComicInfoPresenter
import com.almadevelop.comixreader.screen.list.dialog.info.ComicInfoPresenterImpl
import com.almadevelop.comixreader.screen.list.dialog.info.ComicInfoViewModelImpl
import com.almadevelop.comixreader.service.add.*
import com.almadevelop.comixreader.work.SyncManager
import com.almadevelop.comixreader.work.SyncWorkManager
import com.bumptech.glide.RequestManager
import kotlinx.coroutines.Job
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.scope.bindScope
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import com.almadevelop.comixreader.logic.di.Modules as LogicModules
import com.almadevelop.comixreader.logic.di.ScopeId as LogicScopeId

object Qualifiers {
    const val FRAGMENT = "fragment"
    const val ACTIVITY = "activity"

    const val GLIDE_SCOPE = "glide_scope"
}

private object Modules {
    val all: Array<Module>
        get() = LogicModules.all +
                arrayOf(
                    appModule,
                    componentsModule
                )

    private val appModule = module {
        single<Dispatchers> { AppDispatchers }

        single<SyncManager> { SyncWorkManager(WorkManager.getInstance(androidApplication())) }

        factory<AddComicBookServiceConnector> {
            AddComicBookServiceConnectorImpl(
                androidApplication(),
                get()
            )
        }

        //special scope for glide
        scope(named(Qualifiers.GLIDE_SCOPE)) {
            scoped<RequestManager> {
                getOrNull<Fragment>(named(Qualifiers.FRAGMENT))?.let { GlideApp.with(it) }
                    ?: getOrNull<FragmentActivity>(named(Qualifiers.ACTIVITY))?.let {
                        GlideApp.with(it)
                    }
                    ?: throw Throwable("Koin doesn't have not Activity nor Fragment instance. Use declare method!")
            }
        }
    }

    private val componentsModule = module {
        scope(named<ComicsListFragment>()) {
            scoped<ComicsListPresenter> {
                val fragment = getFragment<ComicsListFragment>()

                ComicsListPresenterImpl(
                    fragment,
                    get(),
                    get(),
                    fragment.viewModel<ComicsViewModelImpl>()
                )
            }

            scoped<ComicsSortDialog.Callback> { getFragment<ComicsListFragment>() }
            scoped<ComicRenameDialog.Callback> { getFragment<ComicsListFragment>() }
            scoped<EditFiltersDialog.Callback> { getFragment<ComicsListFragment>() }
            scoped<AddModeSelectorDialog.Callback> { getFragment<ComicsListFragment>() }
        }

        scope(named<ComicInfoFragment>()) {
            scoped<ComicInfoPresenter> {
                val fragment = getFragment<ComicInfoFragment>()

                ComicInfoPresenterImpl(
                    fragment,
                    get(),
                    fragment.viewModel<ComicInfoViewModelImpl>()
                )
            }
        }

        scope(named<EditFiltersDialog>()) {
            scoped<EditFiltersPresenter> {
                val fragment = getFragment<EditFiltersDialog>()
                val lazySelectedFilters = lazy { EditFiltersDialog.readSelectedFilters(fragment) }

                EditFiltersPresenterImpl(
                    fragment,
                    get(),
                    lazySelectedFilters,
                    fragment.viewModel<EditFiltersViewModelImpl>()
                )
            }
        }

        scope(named<AddComicBookService>()) {
            scoped<AddComicBookPresenter> { (view: AddComicBookView) ->
                AddComicBookPresenterImpl(
                    view,
                    get(),
                    get(),
                    get()
                )
            }
        }

        viewModel {
            //job provided to the pagination factory as parent
            //So, when viewModel's job cancelled child will cancel too
            val job = Job()
            ComicsViewModelImpl(
                get(),
                get(),
                get { parametersOf(job as Job) },
                inject(),
                inject(),
                inject(),
                inject(),
                inject(),
                inject(),
                job
            )
        }

        viewModel { ComicInfoViewModelImpl(get(), inject()) }

        viewModel { EditFiltersViewModelImpl(get(), get()) }
    }
}

/**
 * Declare [Fragment] into the [Scope]. So you can use it inside Scope declaration
 * @param fragment fragment to declare
 */
fun Scope.declareFragment(fragment: Fragment) {
    declare(fragment, named(Qualifiers.FRAGMENT))
}

/**
 * Declare [FragmentActivity] into the [Scope]. So you can use it inside Scope declaration
 * @param activity activity to declare
 */
fun Scope.declareActivity(activity: FragmentActivity) {
    declare(activity, named(Qualifiers.ACTIVITY))
}

/**
 * Get Fragment from scope. Will fail if there is no any Fragment inside the scope
 */
inline fun <reified T : Fragment> Scope.getFragment(): T {
    return get(named(Qualifiers.FRAGMENT))
}

/**
 * Get Activity from scope. Will fail if there is no any Activity inside the scope
 */
inline fun <reified T : FragmentActivity> Scope.getActivity(): T {
    return get(named(Qualifiers.ACTIVITY))
}

/**
 * Helper to create [GlideApp] scope and attach it to the Activity Lifecycle, so it will be destroyed
 */
fun FragmentActivity.getOrCreateGlideScope(): Scope {
    return getOrCreateGlideScopeInner().also { it.declareActivity(this) }
}

/**
 * Helper to create [GlideApp] scope and attach it to the Fragment Lifecycle, so it will be destroyed
 */
fun Fragment.getOrCreateGlideScope(): Scope {
    return getOrCreateGlideScopeInner().also { it.declareFragment(this) }
}

/**
 * Get or create a new [GlideApp] scope and attach it to the provided Lifecycle, so it will be destroyed
 */
private fun <T> T.getOrCreateGlideScopeInner(): Scope where T : ComponentCallbacks, T : LifecycleOwner {
    val koin = getKoin()

    val scopeId = LogicScopeId.GLIDE

    //sync if scope already exist. If not, then create it and bind to the lifecycle
    return koin.getScopeOrNull(scopeId) ?: koin.createScope(scopeId, named(Qualifiers.GLIDE_SCOPE))
        .also { bindScope(it) }
}

fun KoinApplication.setup(app: Application) {
    androidContext(app)
    modules(Modules.all.asList())
}