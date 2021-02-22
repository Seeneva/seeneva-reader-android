package com.almadevelop.comixreader.screen.list.dialog.info

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.cardview.widget.CardView
import androidx.core.text.util.LinkifyCompat
import androidx.core.view.get
import androidx.core.view.updateLayoutParams
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.binding.getValue
import com.almadevelop.comixreader.binding.viewBinding
import com.almadevelop.comixreader.databinding.FragmentComicInfoBinding
import com.almadevelop.comixreader.di.autoInit
import com.almadevelop.comixreader.di.getValue
import com.almadevelop.comixreader.di.koinLifecycleScope
import com.almadevelop.comixreader.extension.inflate
import com.almadevelop.comixreader.extension.observe
import com.almadevelop.comixreader.logic.entity.ComicInfo
import com.almadevelop.comixreader.presenter.PresenterView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.koin.core.scope.KoinScopeComponent
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.util.*

interface ComicInfoView : PresenterView

class ComicInfoFragment : BottomSheetDialogFragment(), ComicInfoView, KoinScopeComponent {
    init {
        setStyle(STYLE_NORMAL, R.style.AppTheme_BottomSheetDialog_FullScreen)
    }

    private val viewBinding by viewBinding(FragmentComicInfoBinding::bind)

    private val lifecycleScope = koinLifecycleScope()

    override val scope by lifecycleScope

    private val presenter by lifecycleScope.autoInit<ComicInfoPresenter>()

    private val bottomSheetBehavior by lazy {
        requireNotNull(dialog).findViewById<View>(R.id.design_bottom_sheet)
            .also {
                it.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
            .let { BottomSheetBehavior.from(it) }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        presenter.comicInfoState
            .observe(this) {
                when (it) {
                    is ComicInfoState.Loading, ComicInfoState.Idle -> viewBinding.contentMessageView.showLoading()
                    ComicInfoState.NotFound -> viewBinding.contentMessageView.showMessage(
                        R.string.comic_info_err_cant_find,
                        0
                    )
                    is ComicInfoState.Success -> {
                        viewBinding.contentMessageView.showContent()

                        inflateInfo(it.comicInfo)
                    }
                    is ComicInfoState.Error -> {
                        viewBinding.contentMessageView
                            .showMessage(
                                R.string.comic_info_err,
                                0
                            )
                    }
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_comic_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.toolbar.also {
            it.setNavigationOnClickListener { dismiss() }

            it.subtitle = bookName
        }
    }

    private fun inflateInfo(comicInfo: ComicInfo) {
        inflateGeneralInfo(comicInfo)
    }

    private fun inflateGeneralInfo(comicInfo: ComicInfo) {
        InfoBuilder(viewBinding.comicInfoLayout)
            .group(R.string.comic_info_general_information) {

                comicInfo.series?.also { row(R.string.comic_info_general_info_series, it) }
                comicInfo.title?.also { row(R.string.comic_info_general_info_title, it) }
                comicInfo.volume?.also {
                    row(
                        R.string.comic_info_general_info_volume,
                        it.toString()
                    )
                }

                if (comicInfo.issue != null && comicInfo.issuesCount != null) {
                    row(
                        R.string.comic_info_general_info_issue,
                        getString(
                            R.string.comic_info_general_info_issue_of,
                            comicInfo.issue,
                            comicInfo.issuesCount
                        )
                    )
                } else if (comicInfo.issue != null) {
                    row(R.string.comic_info_general_info_issue, comicInfo.issue.toString())
                } else if (comicInfo.issuesCount != null) {
                    row(R.string.comic_info_general_info_issues_count, comicInfo.issuesCount.toString())
                }

                row(R.string.comic_info_general_info_pages, comicInfo.pagesCount.toString())

                comicInfo.storyArc?.also {
                    row(
                        R.string.comic_info_general_info_story_arc,
                        it.joinToString()
                    )
                }
                comicInfo.genre?.also {
                    row(R.string.comic_info_general_info_genre, it.joinToString())
                }

                comicInfo.languageIso?.also {
                    row(
                        R.string.comic_info_general_info_language,
                        Locale(it).displayLanguage.orEmpty().ifEmpty { it }.capitalize()
                    )
                }

                comicInfo.notes?.also { row(R.string.comic_info_general_info_notes, it) }
                comicInfo.web?.also {
                    row(
                        R.string.comic_info_general_info_web,
                        it,
                        Linkify.WEB_URLS
                    )
                }

                with(comicInfo.tagNames) {
                    if (isNotEmpty()) {
                        row(R.string.comic_info_general_info_tags, joinToString())
                    }
                }

                row(R.string.comic_info_general_info_source, Uri.decode(comicInfo.path.toString()))

                row(R.string.comic_info_general_info_hash, comicInfo.hash)

                row(R.string.comic_info_general_info_size, comicInfo.formattedSize)
            }.group(R.string.comic_info_credits) {
                comicInfo.writer?.also { row(R.string.comic_info_writets, it.joinToString()) }
                comicInfo.editor?.also { row(R.string.comic_info_editors, it.joinToString()) }
                comicInfo.penciller?.also { row(R.string.comic_info_pencillers, it.joinToString()) }
                comicInfo.inker?.also { row(R.string.comic_info_inkers, it.joinToString()) }
                comicInfo.colorist?.also { row(R.string.comic_info_colorists, it.joinToString()) }
                comicInfo.letterer?.also { row(R.string.comic_info_letterers, it.joinToString()) }
                comicInfo.coverArtist?.also {
                    row(
                        R.string.comic_info_cover_artists,
                        it.joinToString()
                    )
                }
            }.group(R.string.comic_info_release) {
                comicInfo.publisher?.also { row(R.string.comic_info_publisher, it) }
                comicInfo.imprint?.also { row(R.string.comic_info_imprint, it) }
                comicInfo.date?.also { dateAccessor ->
                    when {
                        dateAccessor.isSupported(ChronoField.DAY_OF_MONTH) -> DateTimeFormatter.ofLocalizedDate(
                            FormatStyle.MEDIUM
                        )
                        dateAccessor.isSupported(ChronoField.YEAR) -> DateTimeFormatter.ofPattern("[LLL] uuuu")
                        else -> null
                    }?.format(dateAccessor)
                        ?.capitalize()
                        ?.also { row(R.string.comic_info_date, it) }
                }
                comicInfo.format?.also { row(R.string.comic_info_format, it) }

                comicInfo.blackAndWhite?.also {
                    row(
                        R.string.comic_info_pages_color, if (it) {
                            getString(R.string.comic_info_black_white)
                        } else {
                            getString(R.string.comic_info_colorful)
                        }
                    )
                }
                comicInfo.ageRating?.also { row(R.string.comic_info_age_rating, it) }
            }.group(R.string.comic_info_plot) {
                comicInfo.summary?.also { row(R.string.comic_info_plot_summary, it) }
                comicInfo.characters?.also {
                    row(
                        R.string.comic_info_plot_characters,
                        it.joinToString()
                    )
                }
                comicInfo.teams?.also { row(R.string.comic_info_plot_teams, it.joinToString()) }
                comicInfo.locations?.also {
                    row(
                        R.string.comic_info_plot_locations,
                        it.joinToString()
                    )
                }
            }.build()
    }

    private class InfoBuilder(private val parent: ViewGroup) {
        private val inflater by lazy { LayoutInflater.from(parent.context) }

        private val groups: MutableList<Group> = arrayListOf()

        /**
         * Create new comic book information group
         * @param titleResId title of the book
         * @param f describe function of the group
         */
        inline fun group(@StringRes titleResId: Int, f: GroupBuilder.() -> Unit): InfoBuilder {
            groups += GroupBuilder(titleResId).apply(f).build()
            return this
        }

        /**
         * Build and inflate all comic book info groups
         */
        fun build() {
            groups.forEachIndexed { groupIndex, (titleResId, rows) ->
                if (rows.isNotEmpty()) {
                    val groupLayout = inflateGroupView(titleResId, groupIndex == groups.size - 1)

                    rows.forEachIndexed { index, (titleResId, text, linkMask) ->
                        if (text.isNotBlank()) {
                            inflateRowView(
                                groupLayout,
                                titleResId,
                                text,
                                linkMask,
                                index == rows.size - 1
                            )
                        }
                    }
                }
            }
        }

        private fun inflateGroupView(
            @StringRes infoTitleResId: Int,
            isLast: Boolean = false
        ): ViewGroup {
            parent.inflate<View>(R.layout.layout_comic_info_group, true, inflater)

            val titleView = parent[parent.childCount - 2] as TextView
            val contentView = parent[parent.childCount - 1] as CardView

            titleView.setText(infoTitleResId)

            if (isLast) {
                contentView.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = 0 }
            }

            //Bug on Material Design library
            //MaterialCardViewHelper dows not set any color for foreground. It is black by default
            //https://github.com/material-components/material-components-android/commit/7020b37719fd1ad1ff99396e371cf95232dec4b4
            //TODO remove it when fix will be released
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                contentView.foreground?.also {
                    if (it is GradientDrawable) {
                        it.setColor(Color.TRANSPARENT)
                    }
                }
            }

            return contentView.findViewById(R.id.groupLayout)
        }

        private fun inflateRowView(
            groupLayout: ViewGroup,
            @StringRes infoTitleResId: Int,
            infoText: CharSequence,
            linkMask: Int = 0,
            isLast: Boolean = false
        ) {
            groupLayout.inflate<View>(R.layout.layout_comic_info_item, true, inflater)

            val titleView = groupLayout[groupLayout.childCount - 2] as TextView
            val textView = groupLayout[groupLayout.childCount - 1] as TextView


            titleView.setText(infoTitleResId)

            textView.text = infoText

            if (linkMask != 0) {
                LinkifyCompat.addLinks(textView, linkMask)
            }

            if (isLast) {
                textView.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = 0 }
            }
        }

        class GroupBuilder(private val titleResId: Int) {
            private val rows: MutableList<Row> = arrayListOf()

            /**
             * Add a new row into comic book info group
             * @param titleResId title of the row
             * @param text text of the row
             */
            fun row(
                @StringRes titleResId: Int, text: CharSequence,
                linkMask: Int = 0
            ): GroupBuilder {
                rows += Row(titleResId, text, linkMask)
                return this
            }

            fun build() = Group(titleResId, rows)
        }

        private data class Row(
            @StringRes val titleResId: Int, val text: CharSequence,
            val linkMask: Int
        )

        private data class Group(@StringRes val titleResId: Int, val rows: List<Row>)
    }

    companion object {
        private const val ARG_COMIC_ID = "comic_id"
        private const val ARG_COMIC_NAME = "comic_name"

        private const val NO_COMIC_ID = Long.MIN_VALUE

        /**
         * Init fragment to show the comic book info with provided id
         * @param id id of the comic book
         */
        fun newInstance(id: Long, name: String) =
            ComicInfoFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_COMIC_ID, id)
                    putString(ARG_COMIC_NAME, name)
                }
            }

        val ComicInfoFragment.bookId: Long
            get() = when (val bookId = requireArguments().getLong(ARG_COMIC_ID, NO_COMIC_ID)) {
                NO_COMIC_ID -> throw IllegalArgumentException("No comic book id provided")
                else -> bookId
            }

        val ComicInfoFragment.bookName: String
            get() = requireArguments().getString(ARG_COMIC_NAME)
                ?: throw IllegalArgumentException("No comic book name provided")
    }
}