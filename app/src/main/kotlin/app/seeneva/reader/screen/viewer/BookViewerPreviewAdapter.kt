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

package app.seeneva.reader.screen.viewer

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Spannable
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.text.toSpannable
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import app.seeneva.reader.R
import app.seeneva.reader.databinding.VhViewerPagePreviewBinding
import app.seeneva.reader.extension.waitNextLayout
import app.seeneva.reader.logic.entity.ComicBookPage
import app.seeneva.reader.logic.image.ImageLoader
import app.seeneva.reader.logic.image.ImageLoadingTask
import kotlinx.coroutines.*
import org.tinylog.kotlin.Logger
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates

class BookViewerPreviewAdapter(
    context: Context,
    private val imageLoader: ImageLoader,
    coroutineContext: CoroutineContext,
    private var bookPath: Uri = Uri.EMPTY,
    initPages: List<ComicBookPage>? = null,
    private val callback: Callback? = null,
) : RecyclerView.Adapter<BookViewerPreviewAdapter.PagePreviewViewHolder>() {
    var selectedPage by Delegates.observable(0) { _, oldPagePos, newPagePos ->
        check(newPagePos >= 0) { "Selected page cannot be < 0" }

        notifyItemChanged(oldPagePos)
        notifyItemChanged(newPagePos)
    }

    private val inflater = LayoutInflater.from(context)

    private val differ = AsyncListDiffer(this, PageDiffCallback())

    private val adapterCoroutineContext = coroutineContext + Job(coroutineContext.job)

    init {
        setHasStableIds(true)
        setPages(bookPath, initPages)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        PagePreviewViewHolder(
            VhViewerPagePreviewBinding.inflate(inflater, parent, false),
            bookPath,
            imageLoader,
            adapterCoroutineContext,
            callback
        )

    override fun onBindViewHolder(holder: PagePreviewViewHolder, position: Int) {
        holder.bind(getPage(position), selectedPage == position)
    }

    override fun onViewRecycled(holder: PagePreviewViewHolder) {
        super.onViewRecycled(holder)
        holder.onRecycle()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterCoroutineContext.cancelChildren()
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
        private val viewBinding: VhViewerPagePreviewBinding,
        private val bookPath: Uri,
        private val imageLoader: ImageLoader,
        parentContext: CoroutineContext,
        callback: Callback?
    ) : RecyclerView.ViewHolder(viewBinding.root) {
        private val boldSpan = StyleSpan(Typeface.BOLD)

        private val coroutineScope = CoroutineScope(parentContext + Job(parentContext.job))

        /**
         * Last started preview preparing
         */
        private var preparePreviewJob: Job? = null

        /**
         * Page placeholder drawable
         */
        private val placeholder: Drawable =
            ContextCompat.getColor(itemView.context, R.color.white_alpha_50)
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
                viewBinding.previewPosView.text = positionString.toSpannable()
                    .also {
                        it.setSpan(
                            boldSpan,
                            0,
                            it.length,
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                        )
                    }
                viewBinding.previewPosView.alpha = 1.0f
            } else {
                scalePage(0.7f)
                viewBinding.previewPosView.text = positionString
                viewBinding.previewPosView.alpha = 0.6f
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
                viewBinding.previewPageView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    //lets calculate preview page width using ConstraintLayout
                    dimensionRatio = "W,${pageW / pageH.toFloat()}"
                }

                //wait until layout params will be applied
                viewBinding.previewPageView.waitNextLayout()

                ensureActive()

                // Do not load preview image until view is visible
                // It will decrease summary loading time
                while (!viewBinding.previewPageView.isShown) {
                    (viewBinding.previewPageView.invisibleParent()
                        ?: viewBinding.previewPageView).waitNextLayout()
                }

                ensureActive()

                //we are ready to load
                previewLoadingTask = imageLoader.viewerPreview(
                    bookPath,
                    pagePos,
                    viewBinding.previewPageView,
                    // We need to reset placeholder alpha or it can be transparent sometimes
                    placeholder.apply { alpha = 255 }
                )
            }
        }

        private fun scalePage(scale: Float) {
            viewBinding.previewPageView.apply {
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