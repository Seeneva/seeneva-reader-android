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

package app.seeneva.reader.screen.viewer

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.*
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.addListener
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.view.*
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import app.seeneva.reader.R
import app.seeneva.reader.binding.configCustom
import app.seeneva.reader.binding.getValue
import app.seeneva.reader.binding.viewBinding
import app.seeneva.reader.databinding.ActivityBookViewerBinding
import app.seeneva.reader.databinding.LayoutViewerStatesBinding
import app.seeneva.reader.di.*
import app.seeneva.reader.extension.observe
import app.seeneva.reader.extension.waitLayout
import app.seeneva.reader.logic.entity.ComicBookDescription
import app.seeneva.reader.logic.entity.Direction
import app.seeneva.reader.logic.entity.configuration.ViewerConfig
import app.seeneva.reader.logic.entity.configuration.applyToWindow
import app.seeneva.reader.presenter.PresenterStatefulView
import app.seeneva.reader.screen.viewer.dialog.config.ViewerConfigDialog
import app.seeneva.reader.screen.viewer.page.BookViewerPageFragment
import app.seeneva.reader.screen.viewer.page.BookViewerPageFragment.Companion.pageId
import app.seeneva.reader.screen.viewer.page.entity.PageObjectDirection
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import org.koin.core.scope.KoinScopeComponent
import org.koin.core.scope.get
import kotlin.coroutines.resume
import kotlin.properties.Delegates

interface BookViewerView : PresenterStatefulView {
    /**
     * Show comic book loading
     */
    fun onBookLoading()

    /**
     * Show comic book
     * @param bookDescription comic book description
     */
    fun onBookLoaded(bookDescription: ComicBookDescription)

    /**
     * Show comic book loading error state
     */
    fun onBookLoadError()

    /**
     * Called when opened comic book become corrupted
     */
    fun onBookCorruption()

    /**
     * Called when cannot find desired comic book
     */
    fun onBookNotFound()

    /**
     * Change pages read direction
     */
    fun setPagesDirection(direction: Direction)

    /**
     * Called when [ViewerConfig] has been changed
     */
    fun onConfigChanged(config: ViewerConfig)

    /**
     * Called when comic book cover has been changed
     */
    fun onCoverChanged()
}

class BookViewerActivity :
    AppCompatActivity(R.layout.activity_book_viewer),
    BookViewerView,
    BookViewerPageFragment.Callback,
    KoinScopeComponent {
    private val viewBinding by viewBinding(ActivityBookViewerBinding::bind)

    private val viewerStatesBinding by viewBinding(configCustom {
        LayoutViewerStatesBinding.bind(
            viewBinding.viewerStatesLayout
        )
    })

    private val lifecycleScope = koinLifecycleScope {
        // Create retainScope and link it to Activity scope
        it.linkTo(koinRetainScope(Names.viewerRetainScope).scope)
    }

    /**
     * Koin Lifecycle scope
     */
    override val scope by lifecycleScope

    /**
     * Auto init Activity presenter based on lifecycle
     */
    private val presenter by lifecycleScope.autoInit<BookViewerPresenter>()

    private val systemUiManager by lazy { SystemUiManager(window, lifecycle) }

    private val uiAnimator by lazy {
        UIAnimator(
            viewBinding.toolbar,
            arrayOf(viewBinding.pagesPreviewList, viewBinding.greyOutView),
            lifecycle
        )
    }

    private val viewerPager by lazy {
        ViewerPager(
            viewBinding.pagesPager,
            BookViewerAdapter(this),
        )
    }

    private var viewState by Delegates.observable(ViewState.LOADING) { _, _, newState ->
        invalidateOptionsMenu()

        when (newState) {
            ViewState.LOADED -> {
                viewBinding.pagesPager.isGone = false

                viewerStatesBinding.root.isGone = true
            }
            ViewState.LOADING -> {
                viewBinding.pagesPager.isGone = true

                viewerStatesBinding.apply {
                    root.isGone = false
                    progressBar.isGone = false
                    errorLayout.isGone = true
                }
            }
            ViewState.ERROR -> {
                viewBinding.pagesPager.isGone = true

                viewerStatesBinding.apply {
                    root.isGone = false
                    progressBar.isGone = true
                    errorLayout.isGone = false
                }

                systemUiManager.showState(SystemUiState.SHOWED)
            }
        }
    }

    private val pagesPreviewAdapter by lazy {
        BookViewerPreviewAdapter(
            context = this,
            imageLoader = get(),
            coroutineContext = lifecycle.coroutineScope.coroutineContext,
            callback = object : BookViewerPreviewAdapter.Callback {
                override fun onPageClick(pos: Int) {
                    if (viewerPager.currentItem != pos) {
                        viewerPager.setCurrentItem(pos, true)
                        systemUiManager.showState(SystemUiState.HIDDEN)
                    }
                }
            })
    }

    private val pagesPreviewLayoutManager: LinearLayoutManager
        get() = viewBinding.pagesPreviewList.layoutManager as LinearLayoutManager

    private val gestureDetector by lazy {
        GestureDetectorCompat(
            applicationContext,
            object : GestureDetector.SimpleOnGestureListener() {
                private val hitRect = Rect()

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    return if (viewState != ViewState.LOADED) {
                        false
                    } else {
                        //consume all tap events in case if system ui is visible
                        if (systemUiManager.stateFlow.value == SystemUiState.SHOWED) {
                            if (viewBinding.pagesPreviewList.isTouchInside(e)) {
                                systemUiManager.holdShown()
                            } else {
                                systemUiManager.toggle()
                            }
                            true
                        } else {
                            false
                        }
                    }
                }

                override fun onScroll(
                    e1: MotionEvent,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    if (viewState == ViewState.LOADED && viewBinding.pagesPreviewList.isTouchInside(
                            e1
                        )
                    ) {
                        systemUiManager.holdShown()
                    }

                    return super.onScroll(e1, e2, distanceX, distanceY)
                }

                override fun onFling(
                    e1: MotionEvent,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (viewState == ViewState.LOADED && viewBinding.pagesPreviewList.isTouchInside(
                            e1
                        )
                    ) {
                        systemUiManager.holdShown()
                    }

                    return super.onFling(e1, e2, velocityX, velocityY)
                }

                private fun View.isTouchInside(e: MotionEvent) =
                    isVisible && getHitRect(hitRect)
                        .let { hitRect.contains(e.rawX.toInt(), e.rawY.toInt()) }
            })
    }

    private val pagesChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            //move preview pages to the current selected page
            viewBinding.pagesPreviewList.smoothScrollToPosition(position)

            pagesPreviewAdapter.selectedPage = position

            requireActionBar().subtitle = getString(
                R.string.viewer_preview_page_counter,
                position + 1,
                viewerPager.count
            )

            presenter.onPageChange(position)

            for (fragment in supportFragmentManager.fragments) {
                if (fragment is BookViewerPageFragment) {
                    //reset read state for all pages which is not currently visible page
                    if (fragment.pageId != viewerPager.currentItemId) {
                        fragment.reset()
                    }
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            // On Android 9 dispatchTouchEvent can be called after the Activity was closed
            // https://github.com/Seeneva/seeneva-reader-android/issues/24
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun supportNavigateUpTo(upIntent: Intent) {
        // I don't want to recreate parent Activity
        super.supportNavigateUpTo(upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.comics_viewer, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu) =
        when (viewState) {
            ViewState.LOADED -> super.onPrepareOptionsMenu(menu)
            else -> false
        }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                showSettings()
                true
            }
            R.id.set_cover -> {
                presenter.setPageAsCover(viewerPager.currentItem)
                true
            }
            R.id.swap_horizontally -> {
                presenter.swapDirection()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(viewBinding.toolbar)
        requireActionBar().setDisplayHomeAsUpEnabled(true)

        // Grey view will consume all touches from pages
        // It is all because SubsamplingScaleImageView.isPanEnabled for some reason center page after usage
        @Suppress("ClickableViewAccessibility")
        viewBinding.greyOutView.setOnTouchListener { _, _ -> systemUiManager.stateFlow.value == SystemUiState.SHOWED }

        systemUiManager.stateFlow
            .shouldAnimate()
            .observe(this) { (state, animate) ->
                uiAnimator.showState(state, animate)

                viewBinding.pagesPager.isUserInputEnabled = state == SystemUiState.HIDDEN
            }

        with(viewBinding.pagesPreviewList) {
            setHasFixedSize(true)

            adapter = pagesPreviewAdapter

            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

                if (systemInsets.bottom != 0) {
                    v.translationY = -systemInsets.bottom.toFloat()
                } else {
                    //remove top inset because our preview list can't reach it
                    v.updatePadding(
                        left = v.paddingLeft + systemInsets.left,
                        right = v.paddingRight + systemInsets.right,
                        bottom = v.paddingBottom + systemInsets.bottom
                    )
                }

                insets
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.toolbar) { v, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.updatePadding(
                top = systemInsets.top,
                left = systemInsets.left,
                right = systemInsets.right
            )

            insets
        }
    }

    override fun onBookLoading() {
        viewState = ViewState.LOADING

        viewerPager.unregisterOnPageChangeCallback(pagesChangeCallback)

        viewerPager.setCurrentItem(0, false)
        viewBinding.pagesPreviewList.scrollToPosition(0)
    }

    override fun onBookLoaded(bookDescription: ComicBookDescription) {
        viewState = ViewState.LOADED

        requireActionBar().title = bookDescription.name

        viewerPager.setPages(bookDescription.pages)
        pagesPreviewAdapter.setPages(bookDescription.path, bookDescription.pages)

        setPagesDirection(bookDescription.direction)

        viewerPager.registerOnPageChangeCallback(pagesChangeCallback)

        viewerPager.setCurrentItem(bookDescription.readPosition, false)

        //needed to properly set page on preview list
        viewBinding.pagesPreviewList.scrollToPosition(bookDescription.readPosition)
    }

    override fun onBookLoadError() {
        viewState = ViewState.ERROR
    }

    override fun onBookCorruption() {
        setResult(RESULT_CORRUPTED)
        finish()
    }

    override fun onBookNotFound() {
        setResult(RESULT_NOT_FOUND)
        finish()
    }

    override fun setPagesDirection(direction: Direction) {
        val reverse = when (direction) {
            Direction.LTR -> false
            Direction.RTL -> true
        }

        if (reverse != viewerPager.reverse) {
            viewerPager.reverse = reverse
            pagesPreviewLayoutManager.reverseLayout = reverse
            //reversion transaction will look smoothly
            viewBinding.pagesPreviewList.scrollToPosition(viewerPager.currentItem)
        }
    }

    override fun onConfigChanged(config: ViewerConfig) {
        config.applyToWindow(window)
    }

    override fun onCoverChanged() {
        Snackbar.make(
            viewBinding.root,
            R.string.viewer_cover_changed,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun lastObjectViewed(pageId: Long, direction: PageObjectDirection) {
        //Check if it is called from currently visible page
        if (pageId != viewerPager.currentItemId) {
            return
        }

        val pagePosToShow = if (direction == PageObjectDirection.FORWARD) {
            viewerPager.currentItem + 1
        } else {
            viewerPager.currentItem - 1
        }

        if (pagePosToShow in 0 until viewerPager.count) {
            viewerPager.setCurrentItem(pagePosToShow)
        }
    }

    /**
     * Show comic book view settings
     */
    private fun showSettings() {
        if (supportFragmentManager.findFragmentByTag(TAG_SETTINGS) == null) {
            ViewerConfigDialog.newInstance().show(supportFragmentManager, TAG_SETTINGS)
        }
    }

    private fun requireActionBar(): ActionBar =
        requireNotNull(supportActionBar) { "Action bar should be init" }

    private enum class ViewState {
        LOADING, LOADED, ERROR
    }

    private class UIAnimator(
        private val toolbar: View,
        private val alphaView: Array<View>,
        lifecycle: Lifecycle
    ) : DefaultLifecycleObserver, CoroutineScope by lifecycle.coroutineScope {
        private var animateJob: AnimateJob? = null

        //animate from hide state to the show state
        private val animator: ValueAnimator by lazy {
            ValueAnimator.ofPropertyValuesHolder(
                PropertyValuesHolder.ofFloat(KEY_ALPHA, .0f, 1.0f),
                PropertyValuesHolder.ofFloat(KEY_TRANSLATION, -toolbar.bottom.toFloat(), .0f)
            ).apply {
                addListener(onStart = {
                    if ((it as ValueAnimator).alphaValue == .0f) {
                        alphaView.forEach { v -> v.isVisible = true }
                    }
                }, onEnd = {
                    if ((it as ValueAnimator).alphaValue == .0f) {
                        alphaView.forEach { v -> v.isGone = true }
                    }
                })

                addUpdateListener {
                    val newAlpha = it.alphaValue
                    val newTranslation = it.translationValue

                    alphaView.forEach { v -> v.alpha = newAlpha }

                    toolbar.translationY = newTranslation
                }

                interpolator = FastOutSlowInInterpolator()

                duration = 500L
            }
        }

        init {
            lifecycle.addObserver(this)
        }

        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)

            animator.cancel()
        }

        fun showState(state: SystemUiState, animate: Boolean = true) {
            start(state, animate)
        }

        private fun start(state: SystemUiState, animate: Boolean) {
            val prevAnimateJob = animateJob

            if (prevAnimateJob != null &&
                prevAnimateJob.isActive &&
                prevAnimateJob.state == state &&
                prevAnimateJob.animate == animate
            ) {
                return
            }

            animateJob = AnimateJob(state, animate, launch {
                prevAnimateJob?.cancelAndJoin()

                toolbar.waitLayout()

                ensureActive()

                if (animate) {
                    animator.suspendStart(state)
                } else {
                    animator.currentPlayTime =
                        when (state) {
                            SystemUiState.SHOWED -> animator.duration
                            SystemUiState.HIDDEN -> 0L
                        }
                }
            })
        }

        private class AnimateJob(
            val state: SystemUiState,
            val animate: Boolean,
            job: Job
        ) : Job by job

        companion object {
            private const val KEY_ALPHA = "alpha"
            private const val KEY_TRANSLATION = "translation"

            private val ValueAnimator.alphaValue
                get() = getAnimatedValue(KEY_ALPHA) as Float

            private val ValueAnimator.translationValue
                get() = getAnimatedValue(KEY_TRANSLATION) as Float

            /**
             * Start animator and wait till it end
             */
            private suspend fun ValueAnimator.suspendStart(state: SystemUiState) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val listener = addListener {
                        doOnEnd { cont.resume(Unit) }
                        doOnCancel { cont.cancel() }
                    }

                    cont.invokeOnCancellation {
                        //cancel animator and remove listener
                        cancel()
                        removeListener(listener)
                    }

                    when (state) {
                        SystemUiState.SHOWED -> start()
                        SystemUiState.HIDDEN -> reverse()
                    }
                }
            }
        }
    }

    /**
     * Result contract to open open comic book viewer
     * Pass comic book id as input
     * Output is null or [ResultMessage]
     */
    class OpenViewerContract : ActivityResultContract<Long, ResultMessage?>() {
        override fun createIntent(context: Context, input: Long) =
            openViewer(context, input)

        override fun parseResult(resultCode: Int, intent: Intent?) =
            when (resultCode) {
                RESULT_CORRUPTED -> ResultMessage.CORRUPTED
                RESULT_NOT_FOUND -> ResultMessage.NOT_FOUND
                else -> null
            }
    }

    /**
     * Viewer result
     * @see OpenViewerContract
     */
    enum class ResultMessage {
        /**
         * Comic book was corrupted
         */
        CORRUPTED,

        /**
         * Comic book cannot be found
         */
        NOT_FOUND
    }

    companion object {
        /**
         * Set as Activity result in case if comic book was corrupted
         */
        private const val RESULT_CORRUPTED = 100

        /**
         * Set as Activity result in case if comic book cannot be fount
         */
        private const val RESULT_NOT_FOUND = 101

        private const val ARG_BOOK_ID = "book_id"

        private const val TAG_SETTINGS = "settings"

        /**
         * Open comic book viewer
         * @param context caller context
         * @param bookId comic book id
         * @return viewer open intent
         */
        private fun openViewer(context: Context, bookId: Long): Intent =
            Intent(context, BookViewerActivity::class.java)
                .putExtra(ARG_BOOK_ID, bookId)

        val BookViewerActivity.bookId: Long
            get() = when (val id = intent.getLongExtra(ARG_BOOK_ID, Long.MIN_VALUE)) {
                Long.MIN_VALUE -> throw IllegalStateException("Provide comic book id to open viewer")
                else -> id
            }

        /**
         * Check and set if UI state should be animated
         */
        private fun Flow<SystemUiState>.shouldAnimate(): Flow<Pair<SystemUiState, Boolean>> {
            var first = true

            return transform {
                emit(it to !first)
                first = false
            }
        }
    }
}