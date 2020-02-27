package com.almadevelop.comixreader

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.almadevelop.comixreader.di.setup
import com.almadevelop.comixreader.screen.list.ComicsListPresenter
import com.almadevelop.comixreader.screen.list.ComicsListView
import com.almadevelop.comixreader.service.add.AddComicBookPresenter
import com.almadevelop.comixreader.service.add.AddComicBookView
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.test.check.checkModules

@RunWith(AndroidJUnit4::class)
class DI {
    @Test
    fun check_all_modules() {
        koinApplication { setup(ApplicationProvider.getApplicationContext()) }
            .checkModules {
                create<AddComicBookPresenter> { parametersOf(mockk<AddComicBookView>(relaxed = true)) }
                create<ComicsListPresenter> { parametersOf(mockk<ComicsListView>(relaxed = true)) }
            }
    }
}
