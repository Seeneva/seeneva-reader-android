package com.almadevelop.comixreader.screen

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.minusAssign
import androidx.core.view.plusAssign
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.almadevelop.comixreader.R
import com.almadevelop.comixreader.screen.list.ComicsListFragment
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(toolbar)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(R.id.container, ComicsListFragment())
            }
        }
        //maybe title should be displayed on another one fragment
        requireNotNull(supportActionBar).setDisplayShowTitleEnabled(false)
    }

    override fun onSupportActionModeStarted(mode: ActionMode) {
        super.onSupportActionModeStarted(mode)
        toolbar.visibility = View.INVISIBLE
    }

    override fun onSupportActionModeFinished(mode: ActionMode) {
        super.onSupportActionModeFinished(mode)
        toolbar.visibility = View.VISIBLE
    }

    override fun onAttachFragment(fragment: Fragment) {
        super.onAttachFragment(fragment)
        if (fragment !is MainContent) {
            return
        }

        val addToolbarContent = {
            val toolbarContentView = fragment.activityActionBarContent(toolbar)

            if (toolbarContentView != null) {
                toolbar += toolbarContentView

                //detach view if fragment detached
                fragment.viewLifecycleOwnerLiveData.observe(this, Observer {
                    if (it == null) {
                        toolbar -= toolbarContentView
                    }
                })
            }
        }

        if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            addToolbarContent()
        } else {
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onCreate(owner: LifecycleOwner) {
                    addToolbarContent()
                    lifecycle.removeObserver(this)
                }
            })
        }
    }
}
