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

package app.seeneva.reader.screen.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.core.view.plusAssign
import androidx.fragment.app.Fragment
import app.seeneva.reader.BuildConfig
import app.seeneva.reader.R
import app.seeneva.reader.binding.getValue
import app.seeneva.reader.binding.viewBinding
import app.seeneva.reader.databinding.FragmentAboutAppBinding
import app.seeneva.reader.databinding.LayoutAboutCardBinding
import app.seeneva.reader.screen.about.licenses.ThirdPartyActivity
import app.seeneva.reader.screen.about.licenses.dialog.LicenseDialogFragment
import org.tinylog.kotlin.Logger

class AboutAppFragment : Fragment(R.layout.fragment_about_app) {
    private val viewBinding by viewBinding(FragmentAboutAppBinding::bind)

    private val onClickListener = View.OnClickListener {
        when (it.id) {
            R.id.about_app_web_group ->
                viewUri(getString(R.string.about_app_web_txt).toUri())
            R.id.about_app_donate_group ->
                viewUri(getString(R.string.about_app_donate_link).toUri())
            R.id.about_app_source_code_group ->
                viewUri(getString(R.string.about_app_source_code_link).toUri())
            R.id.about_app_license_group ->
                LicenseDialogFragment.newInstance()
                    .show(childFragmentManager, TAG_APP_LICENSE)
            R.id.about_app_third_party_group ->
                startActivity(ThirdPartyActivity.startIntent(requireContext()))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val appName = getString(R.string.app_name)

        viewBinding.appNameView.text =
            getString(R.string.about_app_name, appName, BuildConfig.VERSION_NAME)

        viewBinding.descriptionView.text =
            getString(R.string.about_app_description, appName)

        newCard(
            R.id.about_app_web_group,
            R.drawable.ic_round_home_24,
            R.string.about_app_web,
            R.string.about_app_web_txt,
            onClickListener
        )

        if (BuildConfig.DONATE_ENABLED) {
            newCard(
                R.id.about_app_donate_group,
                R.drawable.ic_hand_holding_usd_solid,
                R.string.about_app_donate,
                R.string.about_app_donate_txt,
                onClickListener
            )
        }

        newCard(
            R.id.about_app_source_code_group,
            R.drawable.ic_code_branch_solid,
            R.string.about_app_source_code,
            R.string.about_app_source_code_txt,
            onClickListener
        )

        newCard(
            R.id.about_app_license_group,
            R.drawable.ic_round_policy_24,
            R.string.about_app_license,
            R.string.about_app_license_txt,
            onClickListener
        )

        newCard(
            R.id.about_app_third_party_group,
            R.drawable.ic_round_extension_24,
            R.string.about_app_libraries,
            R.string.about_app_libraries_txt,
            onClickListener
        )

        newCard(
            0,
            R.drawable.ic_round_favorite_24,
            R.string.about_app_gratitude,
            R.string.about_app_gratitude_txt
        )
    }

    private fun newCard(
        @IdRes id: Int,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes text: Int,
        onClick: View.OnClickListener? = null
    ) {
        viewBinding.content += LayoutAboutCardBinding.inflate(
            layoutInflater,
            viewBinding.content,
            false
        ).apply {
            root.id = id

            if (onClick != null) {
                root.setOnClickListener(onClick)
            }

            iconView.setImageDrawable(AppCompatResources.getDrawable(iconView.context, icon))
            titleView.setText(title)
            textView.setText(text)
        }.root
    }

    /**
     * Start activity to view provided uri
     * @param uri uri to view
     */
    private fun viewUri(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Logger.error("Can't view uri: $uri")
        }
    }

    companion object {
        private const val TAG_APP_LICENSE = "app_license"
    }
}