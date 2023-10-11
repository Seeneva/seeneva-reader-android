/*
 *  This file is part of Seeneva Android Reader
 *  Copyright (C) 2021-2023 Sergei Solodovnikov
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.seeneva.reader.screen.about.licenses.dialog

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import app.seeneva.reader.R
import app.seeneva.reader.binding.config
import app.seeneva.reader.binding.doOnDestroy
import app.seeneva.reader.binding.getValue
import app.seeneva.reader.binding.viewBinding
import app.seeneva.reader.databinding.DialogLicenseBinding
import app.seeneva.reader.logic.entity.legal.License
import app.seeneva.reader.screen.list.dialog.BaseDraggableDialog

class LicenseDialogFragment : BaseDraggableDialog() {
    private val binding by viewBinding(config(DialogLicenseBinding::bind) doOnDestroy {
        webView.loadUrl("about:blank")
        webView.destroy()
    })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_license, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.webView.settings.apply {
            blockNetworkImage = true
            blockNetworkLoads = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_NO_CACHE

            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                allowUniversalAccessFromFileURLs = false
                allowFileAccessFromFileURLs = false
                // Removed https://developer.android.com/sdk/api_diff/33/changes/android.webkit.WebSettings
                //setAppCacheEnabled(false)
            }
        }

        displayLicense()
    }

    /**
     * Show license file in the fragment
     * @param licenseFileName license file name to show
     */
    fun showThirdPartyLicense(licenseFileName: String) {
        showLicenseInner(licenseFileName, true)
    }

    /**
     * Show the license used by the app
     */
    fun showAppLicense() {
        showLicenseInner(appLicenseFileName, false)
    }

    private fun showLicenseInner(licenseFileName: String, thirdParty: Boolean) {
        if (licenseFileName == requireArguments().licenseFileName && thirdParty == requireArguments().licenseThirdParty) {
            return
        }

        requireArguments().licenseFileName = licenseFileName
        requireArguments().licenseThirdParty = thirdParty

        displayLicense(licenseFileName, thirdParty)
    }

    private fun displayLicense(
        licenseFileName: String = requireArguments().licenseFileName,
        thirdParty: Boolean = requireArguments().licenseThirdParty
    ) {
        val licenseUrl = buildString {
            append("file:///android_asset/license")

            if (thirdParty) {
                append("/third_party")
            }

            append("/${licenseFileName}.html")
        }

        // display license text in the webView
        binding.webView.loadUrl(licenseUrl)
    }

    companion object {
        private const val ARG_LICENSE_FILE_NAME = "license_file_name"
        private const val ARG_LICENSE_THIRD_PARTY = "license_third_party"

        private val appLicenseFileName
            get() = License.GPL3_OR_LATER.id

        private var Bundle.licenseFileName: String
            set(value) = putString(ARG_LICENSE_FILE_NAME, value)
            get() = requireNotNull(
                getString(
                    ARG_LICENSE_FILE_NAME,
                    null
                )
            ) { "License file name should be provided" }

        private var Bundle.licenseThirdParty: Boolean
            set(value) = putBoolean(ARG_LICENSE_THIRD_PARTY, value)
            get() = getBoolean(ARG_LICENSE_THIRD_PARTY, false)

        /**
         * Create new license dialog fragment instance
         * @param licenseFileName name of the license file without extension
         * @return dialog fragment instance
         */
        fun newThirdPartyInstance(licenseFileName: String) =
            newnstanceInner(licenseFileName, true)

        /**
         * Create new license dialog fragment instance
         * @return dialog fragment instance which will show license used by the app
         */
        fun newInstance() =
            newnstanceInner(appLicenseFileName, false)

        private fun newnstanceInner(licenseFileName: String, thirdParty: Boolean) =
            LicenseDialogFragment().also {
                it.arguments = Bundle().also { bundle ->
                    bundle.licenseFileName = licenseFileName
                    bundle.licenseThirdParty = thirdParty
                }
            }
    }
}