package com.almadevelop.comixreader.screen.list.adapter

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.extension.inflate
import com.almadevelop.comixreader.screen.list.entity.FilterLabel

class FiltersAdapter(private val callback: Callback) :
    ListAdapter<FilterLabel, FiltersAdapter.ViewHolder>(this) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent, callback)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        parent: ViewGroup,
        private val callback: Callback
    ) : RecyclerView.ViewHolder(parent.inflate(R.layout.vh_comic_filter)) {
        private val labelView: TextView
            get() = itemView as TextView

        private lateinit var currentFilterLabel: FilterLabel

        init {
            setLabelBackground(labelView)

            labelView.setOnClickListener { callback.onFilterClicked(currentFilterLabel) }
        }

        fun bind(filterLabel: FilterLabel) {
            this.currentFilterLabel = filterLabel

            labelView.text = filterLabel.title
        }

        private companion object {
            /**
             * Set background to a [labelView]
             * @param labelView a view where to set new background
             */
            private fun setLabelBackground(labelView: View) {
                labelView.background =
                    requireNotNull(
                        AppCompatResources.getDrawable(
                            labelView.context,
                            R.drawable.bcg_comic_filter
                        )
                    )

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    val tint = AppCompatResources.getColorStateList(
                        labelView.context,
                        R.color.app_control_highlight
                    )

                    ViewCompat.setBackgroundTintList(labelView, tint)
                }
            }
        }
    }

    interface Callback {
        fun onFilterClicked(filterLabel: FilterLabel)
    }

    companion object : DiffUtil.ItemCallback<FilterLabel>() {
        override fun areItemsTheSame(oldItem: FilterLabel, newItem: FilterLabel): Boolean {
            return oldItem.groupId == newItem.groupId
        }

        override fun areContentsTheSame(oldItem: FilterLabel, newItem: FilterLabel): Boolean {
            return oldItem == newItem
        }
    }
}