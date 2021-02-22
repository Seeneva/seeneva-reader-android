package com.almadevelop.comixreader.screen.viewer.page

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.os.bundleOf
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.*
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.binding.getValue
import com.almadevelop.comixreader.binding.viewBinding
import com.almadevelop.comixreader.databinding.FragmentViewerPageBinding
import com.almadevelop.comixreader.databinding.LayoutViewerStatesBinding
import com.almadevelop.comixreader.di.*
import com.almadevelop.comixreader.extension.*
import com.almadevelop.comixreader.logic.entity.Direction
import com.almadevelop.comixreader.presenter.PresenterStatefulView
import com.almadevelop.comixreader.screen.viewer.page.entity.PageObjectDirection
import com.davemorrissey.labs.subscaleview.ImageViewState
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.koin.core.scope.KoinScopeComponent
import org.koin.core.scope.get
import java.util.*
import kotlin.math.roundToInt

interface BookViewerPageView : PresenterStatefulView

class BookViewerPageFragment :
    Fragment(R.layout.fragment_viewer_page),
    BookViewerPageView,
    PageViewerHelperFragment.Callback,
    KoinScopeComponent {
    private val viewBinding by viewBinding(FragmentViewerPageBinding::bind)
    private val statesViewBinding by viewBinding { LayoutViewerStatesBinding.bind(viewBinding.root) }

    private val lifecycleScope = koinLifecycleScope { it.linkTo(requireActivityScope()) }

    override val scope by lifecycleScope

    private val presenter by lifecycleScope.autoInit<BookViewerPagePresenter>()

    private val callback by lazy { scope.getOrNull<Callback>() }

    private val viewer by lazy {
        PageViewer(
            viewBinding.scaleImageView,
            get(),
            viewLifecycleOwner.lifecycle
        )
    }

    private val objectImageHelper by lazy {
        ObjectImageHelper(
            viewBinding.scaleImageView,
            viewBinding.objectView,
            get(),
        )
    }

    /**
     * Return help fragment instance if it was already showed
     */
    private val helpFragment
        get() = childFragmentManager.findFragmentByTag(TAG_HELPER) as? PageViewerHelperFragment

    private val gestureDetector by lazy {
        GestureDetectorCompat(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            private val viewXCenter = requireView().width * 0.5f

            /**
             * Trigger to close currently visible page object
             */
            private val hideArea = (ResourcesCompat.getFloat(
                requireContext().resources,
                R.dimen.viewer_hide_page_object_x_percentage
            ) * 0.5f).let {
                val d = requireView().width * it

                viewXCenter - d..viewXCenter + d
            }

            private val point = PointF()

            private val rect = Rect()

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isHideObjectBalloonTap(e)) {
                    //this is a zone to hide object
                    hideCurrentPageObject()
                } else {
                    //otherwise we should show new object depends on tap X position

                    val objectDirection =
                        requireNotNull(presenter.readDirectionState.value) { "Read direction is null" }
                            .nextObjectDirectionTap(e)

                    showNextPageObject(objectDirection)
                }

                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                return isHideObjectBalloonTap(e)
            }

            override fun onLongPress(e: MotionEvent) {
                var actionPerformed = false

                //if object view is visible lets check if user pressed on it firstly
                if (objectImageHelper.isPageObjectVisible()) {
                    viewBinding.objectView.getGlobalVisibleRect(rect)

                    if (rect.contains(e.rawX.roundToInt(), e.rawY.roundToInt())) {
                        actionPerformed = presenter.onCurrentPageObjectLongClick()
                    }
                }

                //if touch wasn't consumed, lets check other page objects
                if (!actionPerformed) {
                    //get touch coordinate relative to displayed image
                    val (x, y) = viewBinding.scaleImageView.viewToSourceCoord(e.x, e.y, point)!!

                    actionPerformed = presenter.onPageLongClick(x, y)
                }

                if (actionPerformed) {
                    requireView().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }

            private fun isHideObjectBalloonTap(e: MotionEvent): Boolean {
                return e.x in hideArea
            }

            private fun Direction.nextObjectDirectionTap(e: MotionEvent) =
                when (this) {
                    Direction.LTR -> e.x >= viewXCenter
                    Direction.RTL -> e.x <= viewXCenter
                }.let { isNextTap ->
                    if (isNextTap) {
                        PageObjectDirection.FORWARD
                    } else {
                        PageObjectDirection.BACKWARD
                    }
                }
        })
    }

    private val onTouchListener =
        @Suppress("ClickableViewAccessibility")
        View.OnTouchListener { _, e ->
            gestureDetector.onTouchEvent(e)
            false
        }

    private var snackbar: Snackbar? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setElevation(
            viewBinding.objectView,
            resources.getDimension(R.dimen.viewer_balloon_elevation)
        )

        // Prevent page object top be on top of help fragment
        ViewCompat.setElevation(
            viewBinding.helpContainer,
            resources.getDimension(R.dimen.viewer_balloon_elevation)
        )

        viewLifecycleOwner.lifecycle.subscribeOnImageLoading(
            savedInstanceState?.viewerState,
            savedInstanceState?.isObjectWasVisible ?: false
        )

        presenter.showHelpFlow
            .transformLatest { showHelp ->
                emit(showHelp)

                if (showHelp) {
                    // This will help to show `helpFragment` again after it was removed by `reset` method
                    emitAll(
                        fragmentResumeSignalsFlow().dropWhile { helpFragment != null }
                            .map { showHelp }
                    )
                }
            }
            .observe(viewLifecycleOwner) { showHelp ->
                if (!showHelp) {
                    helpFragment?.remove()
                    return@observe
                }

                viewer.imageEventsStateFlow.collectLatest {
                    when (it) {
                        is PageViewer.PageEvent.Loaded -> coroutineScope { launchHelpDialog() }
                        else -> {
                            // Detach help dialog to show it later when image will be loaded again
                            helpFragment?.detach()
                        }
                    }
                }
            }

        presenter.readDirectionState
            .filterNotNull()
            .drop(1) //drop first non null to prevent reset on first loaded value
            .observe(viewLifecycleOwner) { objectImageHelper.reset() }

        presenter.txtRecognition
            .distinctUntilChanged()
            .observe(viewLifecycleOwner) { state ->
                val msg: CharSequence
                val duration: Int
                val action: Pair<CharSequence, (View) -> Unit>?

                when (state) {
                    is TxtRecognitionState.Idle -> return@observe
                    is TxtRecognitionState.Recogized -> {
                        val txt = state.txt

                        //if text was recognized than show it with copy action
                        if (txt.isNotEmpty()) {
                            msg = txt
                            duration = Snackbar.LENGTH_LONG
                            action = getString(R.string.copy) to {
                                val mng = requireContext().getSystemService<ClipboardManager>()!!
                                mng.setPrimaryClip(ClipData.newPlainText("regognized_txt", txt))
                            }
                        } else {
                            msg = getString(R.string.viewer_txt_recognize_empty)
                            duration = Snackbar.LENGTH_SHORT
                            action = null
                        }
                    }
                    is TxtRecognitionState.Process -> {
                        msg = getString(R.string.viewer_txt_recognize_process)
                        duration = Snackbar.LENGTH_SHORT
                        action = null
                    }
                }

                // sometimes snackbar doesn't showed up without it.
                // Because parent view is not an CoordinatorLayout?
                snackbar?.dismiss()

                snackbar = Snackbar.make(view, msg, duration).also {
                    if (action != null) {
                        it.setAction(action.first, action.second)
                    }

                    it.show()
                }
            }
    }

    override fun onPause() {
        super.onPause()
        //to prevent showing snackbar on wrong pages
        snackbar?.dismiss()
        snackbar = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.apply {
            putBoolean(STATE_OBJECT_WAS_VISIBLE, objectImageHelper.isPageObjectVisible())
            viewBinding.scaleImageView.state?.also { putSerializable(STATE_PAGE_VIEWER, it) }
        }
    }

    override fun onDismissClick() {
        presenter.onUserFinishHelpTips()
    }

    /**
     * Reset page's read state
     */
    fun reset() {
        presenter.resetReadPageObject()
        objectImageHelper.reset()
        helpFragment?.remove()
    }

    private fun Lifecycle.subscribeOnImageLoading(
        _imageViewState: ImageViewState?,
        _showCurrentObject: Boolean
    ) {
        //combine page loading and page display states
        presenter.encodedPageState
            .flatMapLatest { pageState ->
                if (pageState is EncodedPageState.Loaded) {
                    whenStarted {
                        pageState.pageData.img.borrowedObject().apply {
                            viewer.showPage(
                                PageViewer.PageSrc(path, position, width, height)
                            )
                        }
                    }

                    viewer.imageEventsStateFlow.filterNotNull().map { it.asState() }
                } else {
                    flowOf(pageState.asState())
                }
            }
            .distinctUntilChanged()
            .run {
                //Restore last viewed object
                var showCurrentObject = _showCurrentObject
                var imageViewState = _imageViewState

                observe(this@subscribeOnImageLoading) {
                    showState(it)

                    if (it == State.LOADED) {
                        // Help dialog will set own center and scale
                        if (helpFragment == null) {
                            //without it [scaleImageView] will not show anything after restore state (screen rotate)
                            when (val ivs = imageViewState) {
                                null -> viewBinding.scaleImageView.resetScaleAndCenter()
                                else -> {
                                    viewBinding.scaleImageView.setScaleAndCenter(
                                        ivs.scale,
                                        ivs.center
                                    )
                                }
                            }
                        }

                        imageViewState = null

                        if (showCurrentObject) {
                            showCurrentObject = false

                            val obj = presenter.currentPageObject()

                            //show restored object
                            if (obj != null) {
                                objectImageHelper.showPageObject(obj, animate = false)
                            }
                        }
                    }
                }
            }
    }

    private fun showState(state: State) {
        when (state) {
            State.LOADING -> {
                statesViewBinding.progressBar.isVisible = true

                viewBinding.scaleImageView.apply {
                    isEnabled = false
                    isGone = false
                    alpha = .0f
                    setOnTouchListener(null)
                }

                statesViewBinding.errorLayout.isGone = true
            }
            State.LOADED -> {
                statesViewBinding.progressBar.isVisible = false

                viewBinding.scaleImageView.apply {
                    isEnabled = true
                    isGone = false
                    alpha = 1.0f
                    setOnTouchListener(onTouchListener)
                }

                statesViewBinding.errorLayout.isGone = true
            }
            State.ERROR -> {
                statesViewBinding.progressBar.isVisible = false

                viewBinding.scaleImageView.apply {
                    isEnabled = false
                    isGone = true
                    setOnTouchListener(null)
                }

                statesViewBinding.errorLayout.isGone = false
            }
        }
    }

    /**
     * Show next comic book page object if any
     * @param objectDirection
     */
    private fun showNextPageObject(objectDirection: PageObjectDirection) {
        val objData = presenter.nextPageObject(objectDirection)

        if (objData != null) {
            objectImageHelper.showPageObject(objData)

            requireView().performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
            )
        } else {
            callback?.lastObjectViewed(pageId, objectDirection)
        }
    }

    /**
     * Hide current visible page object if any
     */
    private fun hideCurrentPageObject() {
        objectImageHelper.hidePageObject()
    }

    private fun CoroutineScope.launchHelpDialog() {
        // launch flow to help cancel it without parent cancellation
        launch {
            flow {
                val context = currentCoroutineContext()

                val helpDialog =
                    helpFragment ?: PageViewerHelperFragment().also { it.show() }

                // Reattach dialog if it was detached during image loading/error
                if (helpDialog.isDetached) {
                    helpDialog.attach()
                }

                val listener =
                    object : FragmentManager.FragmentLifecycleCallbacks() {
                        override fun onFragmentDestroyed(
                            fm: FragmentManager,
                            f: Fragment
                        ) {
                            super.onFragmentDestroyed(fm, f)
                            if (f === helpDialog) {
                                context.cancel()
                            }
                        }
                    }

                parentFragmentManager.registerFragmentLifecycleCallbacks(listener, false)

                try {
                    emitAll(helpDialog.lastFinishedTipFLow.map { helpDialog to it }
                        .distinctUntilChangedBy { it.second })
                } finally {
                    parentFragmentManager.unregisterFragmentLifecycleCallbacks(listener)
                }
            }.collectLatest { (helpDialog, tipId) ->
                // here we listen for last success completed tips by user
                when (tipId) {
                    // Show next balloon tip was finished
                    TIP_NEXT_BALLOON_ID -> {
                        viewBinding.scaleImageView.resetScaleAndCenter()

                        if (presenter.currentPageObject() == null) {
                            showNextPageObject(PageObjectDirection.FORWARD)
                        }

                        helpDialog.showTip(
                            TIP_HIDE_BALLOON_ID,
                            viewBinding.root.width * 0.5f,
                            viewBinding.root.height * 0.5f,
                            PageViewerHelperFragment.Type.TAP
                        )
                    }
                    // Hide current balloon tip was finished
                    TIP_HIDE_BALLOON_ID -> {
                        hideCurrentPageObject()

                        val objBbox = presenter.currentPageObject()!!.bbox

                        val siv = viewBinding.scaleImageView

                        // reuse purpose
                        val point = PointF()

                        val tipAnimationListenerJob = launch {
                            siv.stateChangedFlow()
                                .collect {
                                    val (x, y) = siv.sourceToViewCoord(
                                        point.apply {
                                            set(
                                                objBbox.centerX(),
                                                objBbox.centerY()
                                            )
                                        }
                                    )!!

                                    helpDialog.showTip(
                                        TIP_TTS_BALLOON_ID,
                                        x,
                                        y,
                                        PageViewerHelperFragment.Type.HOLD
                                    )
                                }
                        }

                        try {
                            siv.animateScaleAndCenterSuspended(
                                siv.maxScale,
                                point.apply { set(objBbox.centerX(), objBbox.centerY()) }
                            )
                        } finally {
                            // Cancel tip animation listener
                            tipAnimationListenerJob.cancel()
                        }
                    }
                    // Start balloon TTS tip was finished
                    TIP_TTS_BALLOON_ID -> {
                        if (presenter.onCurrentPageObjectLongClick()) {
                            requireView().performHapticFeedback(
                                HapticFeedbackConstants.LONG_PRESS
                            )
                        }

                        helpDialog.remove()

                        presenter.onUserFinishHelpTips()
                    }
                    // No tip was finished
                    PageViewerHelperFragment.NO_TIP_ID -> {
                        viewBinding.scaleImageView.resetScaleAndCenter()

                        // Tip X position depends on read direction
                        val readDirection =
                            presenter.readDirectionState.filterNotNull()
                                .first()

                        helpDialog.showTip(
                            TIP_NEXT_BALLOON_ID,
                            when (readDirection) {
                                Direction.LTR -> viewBinding.root.width.toFloat()
                                Direction.RTL -> .0f
                            },
                            viewBinding.root.height * 0.5f,
                            PageViewerHelperFragment.Type.TAP
                        )
                    }
                }

            }
        }
    }

    private fun PageViewerHelperFragment.show() {
        this@BookViewerPageFragment.childFragmentManager.commit {
            add(R.id.helpContainer, this@show, TAG_HELPER)
        }
    }

    private fun PageViewerHelperFragment.attach() {
        this@BookViewerPageFragment.childFragmentManager.commit { attach(this@attach) }
    }

    private fun PageViewerHelperFragment.detach() {
        this@BookViewerPageFragment.childFragmentManager.commit { detach(this@detach) }
    }

    private fun PageViewerHelperFragment.remove() {
        this@BookViewerPageFragment.childFragmentManager.commit { remove(this@remove) }
    }

    /**
     * Send Fragment's onResume signals
     */
    private fun fragmentResumeSignalsFlow() =
        callbackFlow<Unit> {
            val observer = object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    super.onResume(owner)
                    offer(Unit)
                }
            }
            lifecycle.addObserver(observer)

            awaitClose { lifecycle.removeObserver(observer) }
        }

    interface Callback {
        /**
         * Called than last object on the page was viewed
         * @param pageId current page id
         * @param direction object read direction
         */
        fun lastObjectViewed(pageId: Long, direction: PageObjectDirection)
    }

    /**
     * Page viewer state
     */
    private enum class State {
        LOADED, LOADING, ERROR
    }

    companion object {
        private const val ARGS_ID = "page_id"

        private const val STATE_OBJECT_WAS_VISIBLE = "object_was_visible"
        private const val STATE_PAGE_VIEWER = "page_viewer"

        const val TAG_HELPER = "helper_fragment"

        private const val TIP_NEXT_BALLOON_ID = 0
        private const val TIP_HIDE_BALLOON_ID = 1
        private const val TIP_TTS_BALLOON_ID = 2

        private val Bundle.isObjectWasVisible
            get() = getBoolean(STATE_OBJECT_WAS_VISIBLE)

        private val Bundle.viewerState
            get() = getSerializable(STATE_PAGE_VIEWER) as ImageViewState?

        private fun PageViewer.PageEvent.asState() =
            when (this) {
                is PageViewer.PageEvent.Loading -> State.LOADING
                is PageViewer.PageEvent.Error -> State.ERROR
                PageViewer.PageEvent.Loaded -> State.LOADED
            }

        private fun EncodedPageState.asState() =
            when (this) {
                is EncodedPageState.Loading, EncodedPageState.Idle -> State.LOADING
                is EncodedPageState.Loaded -> State.LOADED
                is EncodedPageState.Error -> State.ERROR
            }

        /**
         * Create new page [Fragment]
         *
         * @param pageId comic book page id which should be open
         */
        fun newInstance(pageId: Long) =
            BookViewerPageFragment()
                .apply { arguments = bundleOf(ARGS_ID to pageId) }

        val BookViewerPageFragment.pageId
            get() = when (val pageId =
                requireArguments().getLong(ARGS_ID, Long.MIN_VALUE)) {
                Long.MIN_VALUE -> throw IllegalStateException("Provide comic book page id which should be open")
                else -> pageId
            }
    }
}