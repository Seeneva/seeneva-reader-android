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
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.seeneva.reader.R
import app.seeneva.reader.binding.config
import app.seeneva.reader.binding.doOnDestroy
import app.seeneva.reader.binding.getValue
import app.seeneva.reader.binding.viewBinding
import app.seeneva.reader.databinding.ActivityThirdPartiesBinding
import app.seeneva.reader.di.autoInit
import app.seeneva.reader.di.getValue
import app.seeneva.reader.di.koinLifecycleScope
import app.seeneva.reader.presenter.PresenterView
import app.seeneva.reader.screen.about.licenses.dialog.LicenseDialogFragment
import kotlinx.coroutines.launch
import org.koin.core.scope.KoinScopeComponent
import org.tinylog.kotlin.Logger

interface ThirdPartyView : PresenterView

class ThirdPartyActivity :
    AppCompatActivity(R.layout.activity_third_parties),
    ThirdPartyView,
    KoinScopeComponent {
    private val viewBinding by viewBinding(config(ActivityThirdPartiesBinding::bind) doOnDestroy {
        recyclerView.adapter = null
    })

    private val lifecycleKoinScope = koinLifecycleScope()

    override val scope by lifecycleKoinScope

    private val presenter by lifecycleKoinScope.autoInit<ThirdPartyPresenter>()

    private val adapter by lazy { ThirdPartyAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(viewBinding.toolbar)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)


        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    adapter.clickEvents
                        .collect { position ->
                            when (val thirdPartiesState = presenter.thirdPartyState.value) {
                                is ThirdPartyState.Success -> {
                                    val clickedLicense =
                                        thirdPartiesState.thirdParties[position].license

                                    showLicense(clickedLicense.textFileName)
                                }

                                else -> Logger.error("Can't process click event. Third parties is not loaded yet.")
                            }
                        }
                }

                launch {
                    presenter.thirdPartyState
                        .collect {
                            when (it) {
                                is ThirdPartyState.Success -> {
                                    adapter.show(it.thirdParties)

                                    if (viewBinding.recyclerView.adapter == null) {
                                        viewBinding.recyclerView.adapter = adapter
                                    }

                                    viewBinding.contentMessageView.showContent()
                                }

                                ThirdPartyState.Idle, is ThirdPartyState.Loading ->
                                    viewBinding.contentMessageView.showLoading()

                                is ThirdPartyState.Error -> {
                                    // licenses are store locally so in case of error it is a bug
                                    throw it.t
                                }
                            }
                        }
                }
            }
        }
    }

    override fun supportNavigateUpTo(upIntent: Intent) {
        // I don't want to recreate parent Activity
        super.supportNavigateUpTo(upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    /**
     * Show license by license file name
     * @param licenseFileName license file name to show
     */
    private fun showLicense(licenseFileName: String) {
        val licenseFragment =
            supportFragmentManager.findFragmentByTag(TAG_LICENSE_FRAGMENT) as? LicenseDialogFragment

        if (licenseFragment != null) {
            licenseFragment.showThirdPartyLicense(licenseFileName)
        } else {
            LicenseDialogFragment.newThirdPartyInstance(licenseFileName)
                .show(supportFragmentManager, TAG_LICENSE_FRAGMENT)
        }
    }

    companion object {
        fun startIntent(context: Context) =
            Intent(context, ThirdPartyActivity::class.java)

        private const val TAG_LICENSE_FRAGMENT = "license_fragment"
    }
}