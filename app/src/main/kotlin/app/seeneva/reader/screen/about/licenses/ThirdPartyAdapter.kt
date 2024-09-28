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

package app.seeneva.reader.screen.about.licenses

import android.content.Context
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.text.set
import androidx.core.text.toSpannable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import app.seeneva.reader.R
import app.seeneva.reader.databinding.LayoutComicInfoGroupBinding
import app.seeneva.reader.databinding.LayoutComicInfoItemBinding
import app.seeneva.reader.databinding.VhThirdPartyBinding
import app.seeneva.reader.logic.entity.legal.ThirdParty
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ThirdPartyAdapter(context: Context) : RecyclerView.Adapter<ThirdPartyAdapter.ViewHolder>() {
    private val _clickEvents = MutableSharedFlow<Int>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val clickEvents = _clickEvents.asSharedFlow()

    private val inflater = LayoutInflater.from(context)

    private val thirdParties: MutableList<ThirdParty> = arrayListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(inflater, _clickEvents, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(thirdParties[position])
    }

    override fun getItemCount() = thirdParties.size

    fun show(newThirdParties: List<ThirdParty>?) {
        thirdParties.clear()

        if (!newThirdParties.isNullOrEmpty()) {
            thirdParties += newThirdParties
        }
    }

    interface Callback

    class ViewHolder(
        private val layoutInflater: LayoutInflater,
        private val clickEvents: MutableSharedFlow<Int>,
        parent: ViewGroup
    ) : RecyclerView.ViewHolder(VhThirdPartyBinding.inflate(layoutInflater, parent, false).root) {
        private val binding = LayoutComicInfoGroupBinding.bind(itemView)

        private val versionBinding = newItem(R.string.third_parties_version)
        private val authorsBinding = newItem(R.string.third_parties_authors)
        private val licenseBinding = newItem(R.string.third_parties_license)
        private val homepageBinding = newItem(R.string.third_parties_homepage)
        private val summaryBinding = newItem(R.string.third_parties_summary)

        init {
            binding.groupCardView.setOnClickListener {
                val position = absoluteAdapterPosition

                if (position != RecyclerView.NO_POSITION) {
                    clickEvents.tryEmit(position)
                }
            }
        }

        fun bind(thirdParty: ThirdParty) {
            binding.groupNameView.text = thirdParty.name

            if (thirdParty.version.isEmpty()) {
                versionBinding.isVisible = false
            } else {
                versionBinding.isVisible = true

                versionBinding.itemValueView.text = thirdParty.version
            }

            authorsBinding.itemValueView.text = thirdParty.authors
            licenseBinding.itemValueView.setText(thirdParty.license.type.nameResId)

            homepageBinding.itemValueView.text = thirdParty.homepage
                .toSpannable()
                .also { it[0, it.length] = URLSpan(thirdParty.homepage) }
            homepageBinding.itemValueView.movementMethod = LinkMovementMethod.getInstance()

            summaryBinding.itemValueView.text = thirdParty.summary
        }

        private fun newItem(@StringRes nameResId: Int) =
            LayoutComicInfoItemBinding.inflate(layoutInflater, binding.groupLayout)
                .apply {
                    // Hacky. To allow use multiple bindings with <merge> root item
                    itemNameView.id = View.generateViewId()
                    itemValueView.id = View.generateViewId()

                    itemNameView.setText(nameResId)
                }

        private var LayoutComicInfoItemBinding.isVisible: Boolean
            set(value) {
                itemNameView.isVisible = value
                itemValueView.isVisible = value
            }
            get() = itemNameView.isVisible && itemValueView.isVisible
    }
}