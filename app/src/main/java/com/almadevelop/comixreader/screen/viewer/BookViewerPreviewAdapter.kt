package com.almadevelop.comixreader.screen.viewer

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Spannable
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.text.toSpannable
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.extension.findViewByIdCompat
import com.almadevelop.comixreader.extension.inflate
import com.almadevelop.comixreader.extension.waitNextLayout
import com.almadevelop.comixreader.logic.entity.ComicBookPage
import com.almadevelop.comixreader.logic.image.ImageLoader
import com.almadevelop.comixreader.logic.image.ImageLoadingTask
import kotlinx.coroutines.*
import org.tinylog.kotlin.Logger
import kotlin.properties.Delegates

class BookViewerPreviewAdapter(
    private val imageLoader: ImageLoader,
    private var bookPath: Uri = Uri.EMPTY,
    initPages: List<ComicBookPage>? = null,
    private val callback: Callback? = null,
    parentJob: Job? = null,
) : RecyclerView.Adapter<BookViewerPreviewAdapter.PagePreviewViewHolder>() {
    var selectedPage by Delegates.observable(0) { _, oldPagePos, newPagePos ->
        check(newPagePos >= 0) { "Selected page cannot be < 0" }

        notifyItemChanged(oldPagePos)
        notifyItemChanged(newPagePos)
    }

    private val differ = AsyncListDiffer(this, PageDiffCallback())

    private val job = Job(parentJob)

    init {
        setHasStableIds(true)
        setPages(bookPath, initPages)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagePreviewViewHolder {
        return PagePreviewViewHolder(parent, bookPath, imageLoader, job, callback)
    }

    override fun onBindViewHolder(holder: PagePreviewViewHolder, position: Int) {
        holder.bind(getPage(position), selectedPage == position)
    }

    override fun onViewRecycled(holder: PagePreviewViewHolder) {
        super.onViewRecycled(holder)
        holder.onRecycle()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        job.cancelChildren()
    }

    override fun getItemCount() =
        differ.currentList.size

    override fun getItemId(position: Int) = getPage(position).position

    fun setPages(bookPath: Uri, pages: List<ComicBookPage>?) {
        this.bookPath = bookPath
        differ.submitList(pages)
    }

    private fun getPage(pos: Int) = differ.currentList[pos]

    interface Callback {
        /**
         * User clicker on preview page
         */
        fun onPageClick(pos: Int)
    }

    class PagePreviewViewHolder(
        parent: ViewGroup,
        private val bookPath: Uri,
        private val imageLoader: ImageLoader,
        parentJob: Job,
        callback: Callback?
    ) : RecyclerView.ViewHolder(parent.inflate(R.layout.vh_viewer_page_preview)) {
        private val previewPosView = itemView.findViewByIdCompat<TextView>(R.id.previewPosView)
        private val previewPageView = itemView.findViewByIdCompat<ImageView>(R.id.previewPageView)

        private val boldSpan = StyleSpan(Typeface.BOLD)

        private val coroutineScope = CoroutineScope(Job(parentJob) + Dispatchers.Main.immediate)

        /**
         * Last started preview preparing
         */
        private var preparePreviewJob: Job? = null

        /**
         * Page placeholder drawable
         */
        private val placeholder: Drawable =
            ContextCompat.getColor(parent.context, R.color.white_alpha_50)
                .toDrawable()

        private var previewLoadingTask: ImageLoadingTask? = null

        private var _page: ComicBookPage? = null

        private val page
            get() = checkNotNull(_page) { "Page should be set" }

        init {
            if (callback != null) {
                itemView.setOnClickListener {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        callback.onPageClick(adapterPosition)
                    } else {
                        Logger.error("Invalid viewer preview adapter position: $adapterPosition")
                    }
                }
            }
        }

        fun bind(page: ComicBookPage, selected: Boolean) {
            this._page = page

            val positionString = (adapterPosition + 1).toString()

            if (selected) {
                scalePage(1.0f)
                previewPosView.text = positionString.toSpannable()
                    .also {
                        it.setSpan(
                            boldSpan,
                            0,
                            it.length,
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                        )
                    }
                previewPosView.alpha = 1.0f
            } else {
                scalePage(0.7f)
                previewPosView.text = positionString
                previewPosView.alpha = 0.6f
            }

            preparePreviewJob = coroutineScope.loadPreview()
        }

        fun onRecycle() {
            coroutineScope.coroutineContext.cancelChildren()
            previewLoadingTask?.dispose()
            previewLoadingTask = null
        }

        private fun CoroutineScope.loadPreview(): Job {
            val prevJob = preparePreviewJob

            // Set variables to help closure capture valid values
            val pageW = page.width
            val pageH = page.height
            val pagePos = page.position

            return launch {
                prevJob?.cancelAndJoin()
                previewLoadingTask?.dispose()
                previewLoadingTask = null

                // Firstly we need to prepare preview ImageView by change it size
                previewPageView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    //lets calculate preview page width using ConstraintLayout
                    dimensionRatio = "W,${pageW / pageH.toFloat()}"
                }

                //wait until layout params will be applied
                previewPageView.waitNextLayout()

                ensureActive()

                // Do not load preview image until view is visible
                // It will decrease summary loading time
                while (!previewPageView.isShown) {
                    (previewPageView.invisibleParent() ?: previewPageView).waitNextLayout()
                }

                ensureActive()

                //we are ready to load
                previewLoadingTask = imageLoader.viewerPreview(
                    bookPath,
                    pagePos,
                    previewPageView,
                    // We need to reset placeholder alpha or it can be transparent sometimes
                    placeholder.apply { alpha = 255 }
                )
            }
        }

        private fun scalePage(scale: Float) {
            previewPageView.apply {
                scaleX = scale
                scaleY = scale
            }
        }

        /**
         * @return first invisible parent or null if there is no invisible parent
         */
        private fun View.invisibleParent(): View? {
            if (!isVisible) {
                return this
            }

            val parent = parent as? View ?: return null

            return parent.invisibleParent()
        }
    }
}