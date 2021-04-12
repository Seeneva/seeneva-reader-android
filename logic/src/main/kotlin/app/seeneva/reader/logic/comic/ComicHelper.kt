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

package app.seeneva.reader.logic.comic

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import java.io.File

object ComicHelper {
    /**
     * Average comic page width
     */
    const val PAGE_WIDTH = 1988

    /**
     * Average comic page height
     */
    const val PAGE_HEIGHT = 3056

    const val PAGE_RATIO = PAGE_HEIGHT.toFloat() / PAGE_WIDTH

    /**
     * Intent what search for file manager in an app market
     */
    val installFileManagerIntent: Intent
        get() = Intent(Intent.ACTION_VIEW).setData("market://search?q=file manager&c=apps".toUri())

    internal val persistPermissions: Int
        get() = Intent.FLAG_GRANT_READ_URI_PERMISSION

    /**
     * @param context
     * @return path to the app inner comic book library directory
     */
    internal fun innerComicBookLibraryDir(context: Context): File =
        (context.getExternalFilesDir(null) ?: context.filesDir)
            .resolve("comic_library")
            .also {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }

    /**
     * @param addComicBookMode comic book open mode
     * @return open comic book intent. Depends on [addComicBookMode] and Android version
     */

    fun openComicBookIntent(addComicBookMode: AddComicBookMode): Intent {
        val baseOpenIntent =
            Intent().addCategory(Intent.CATEGORY_OPENABLE) //without it you can receive a "virtual" file
                .setFlags(persistPermissions)
                .setType("*/*")
                .also {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    //.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-cbr"))
                }

        //on pre kit kat devices we have no choice. Any provided content can remove read permission at any time.
        //So it is more reliable to copy files into our app directory
        val resultMode = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            AddComicBookMode.Import
        } else {
            addComicBookMode
        }

        return when (resultMode) {
            AddComicBookMode.Import -> baseOpenIntent.setAction(Intent.ACTION_GET_CONTENT)
            AddComicBookMode.Link ->
                //we have already check Android version. So supress warning
                @Suppress("InlinedApi")
                baseOpenIntent.setAction(Intent.ACTION_OPEN_DOCUMENT)
                    .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

        }
    }
}

@RequiresApi(Build.VERSION_CODES.KITKAT)
fun ContentResolver.releaseComicPermission(path: Uri) {
    releasePersistableUriPermission(path, ComicHelper.persistPermissions)
}

@RequiresApi(Build.VERSION_CODES.KITKAT)
fun ContentResolver.takeComicPermission(path: Uri) {
    takePersistableUriPermission(path, ComicHelper.persistPermissions)
}