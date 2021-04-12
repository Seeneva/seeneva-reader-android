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

package app.seeneva.reader.screen

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.core.view.isInvisible
import androidx.core.view.minusAssign
import androidx.core.view.plusAssign
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commit
import app.seeneva.reader.R
import app.seeneva.reader.binding.getValue
import app.seeneva.reader.binding.viewBinding
import app.seeneva.reader.databinding.ActivityMainBinding
import app.seeneva.reader.extension.inflate
import app.seeneva.reader.screen.about.AboutAppFragment
import app.seeneva.reader.screen.list.ComicsListFragment

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private val viewBinding by viewBinding(ActivityMainBinding::bind)

    /**
     * View that was added to the toolbar during fragment transaction
     */
    private var customToolbarContent: View? = null

    /**
     * Started [ActionMode] instance
     */
    private var startedActionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = customFragmentFactory()
        super.onCreate(savedInstanceState)
        setSupportActionBar(viewBinding.toolbar)

        viewBinding.bottomNavigationView.setOnNavigationItemReselectedListener {
            //DO NOTHING for now...
        }

        viewBinding.bottomNavigationView.setOnNavigationItemSelectedListener { onBottomMenuSelect(it.itemId) }

        if (savedInstanceState == null) {
            onBottomMenuSelect(viewBinding.bottomNavigationView.selectedItemId)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        onBottomMenuSelect(viewBinding.bottomNavigationView.selectedItemId)
    }

    override fun onDestroy() {
        super.onDestroy()
        customToolbarContent = null
        startedActionMode = null
    }

    override fun onSupportActionModeStarted(mode: ActionMode) {
        super.onSupportActionModeStarted(mode)
        startedActionMode = mode
        viewBinding.toolbar.isInvisible = true
    }

    override fun onSupportActionModeFinished(mode: ActionMode) {
        super.onSupportActionModeFinished(mode)
        startedActionMode = null
        viewBinding.toolbar.isInvisible = false
    }

    private fun onBottomMenuSelect(itemId: Int) =
        when (itemId) {
            R.id.library -> {
                requireActionBar().setDisplayShowTitleEnabled(false)
                replaceContentFragment<ComicsListFragment>()
                true
            }
            R.id.about -> {
                requireActionBar().also {
                    it.setDisplayShowTitleEnabled(true)
                    it.title = resources.getString(R.string.about_app)
                }
                replaceContentFragment<AboutAppFragment>()
                true
            }
            else -> false
        }

    private inline fun <reified T : Fragment> replaceContentFragment() {
        if (supportFragmentManager.findFragmentByTag(TAG_CONTENT) is T) {
            // We already show that fragment
            return
        }

        startedActionMode?.finish()

        customToolbarContent?.also { viewBinding.toolbar -= it }
        customToolbarContent = null

        supportFragmentManager.commit {
            setReorderingAllowed(true)

            replace(R.id.container, T::class.java, null, TAG_CONTENT)
        }
    }

    private fun customFragmentFactory() = object : FragmentFactory() {
        val defaultFactory = supportFragmentManager.fragmentFactory

        override fun instantiate(classLoader: ClassLoader, className: String) =
            when (className) {
                ComicsListFragment::class.java.name -> {
                    ComicsListFragment(lazy {
                        viewBinding.toolbar
                            .inflate<SearchView>(R.layout.layout_main_search)
                            .also {
                                it.isSubmitButtonEnabled = false
                                it.isFocusable = false
                                it.isIconified = false

                                it.clearFocus()

                                customToolbarContent = it

                                viewBinding.toolbar += it
                            }
                    })
                }
                else -> defaultFactory.instantiate(classLoader, className)
            }
    }

    private fun requireActionBar() =
        requireNotNull(supportActionBar)

    companion object {
        private const val TAG_CONTENT = "content"
    }
}
