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

package app.seeneva.reader.logic.results

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import app.seeneva.reader.logic.comic.AddComicBookMode
import app.seeneva.reader.logic.comic.ComicHelper

/**
 * Open comic book system chooser [ActivityResultContract]
 * Allows to choose between adding modes described in [AddComicBookMode]
 * Will return null output in case if opening was cancelled
 */
class ChooseComicBookContract : ActivityResultContract<AddComicBookMode, ChooseComicBookResult?>() {
    override fun createIntent(context: Context, input: AddComicBookMode): Intent {
        val baseOpenIntent =
            Intent().addCategory(Intent.CATEGORY_OPENABLE) //without it you can receive a "virtual" file
                .setFlags(ComicHelper.persistPermissions)
                .setType("*/*")
                .also {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    //.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-cbr"))
                }

        //on pre kit kat devices we have no choice. Any provided content can remove read permission at any time.
        //So it is more reliable to copy files into our app directory
        val mode = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            AddComicBookMode.Import
        } else {
            input
        }

        return when (mode) {
            AddComicBookMode.Import -> baseOpenIntent.setAction(Intent.ACTION_GET_CONTENT)
            AddComicBookMode.Link ->
                //we have already check Android version. So suppress warning
                @Suppress("InlinedApi")
                baseOpenIntent.setAction(Intent.ACTION_OPEN_DOCUMENT)
                    .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?) =
        if (resultCode == Activity.RESULT_OK) {
            requireNotNull(intent) { "Comic books opening result doesn't have any data" }

            val dataContent = intent.data
            val dataClipData = intent.clipData

            val paths = when {
                dataContent != null ->
                    listOf(dataContent)
                dataClipData != null && dataClipData.itemCount > 0 ->
                    (0 until dataClipData.itemCount).map { dataClipData.getItemAt(it).uri }
                else ->
                    throw IllegalStateException("Result intent doesn't have any data: $intent")
            }

            ChooseComicBookResult(paths, intent.flags)
        } else {
            null
        }
}

/**
 * User chose a comic book(s) to add into the library
 * @param paths comic book path(s)
 * @param permissionFlags [Intent] permission flags, use it to run another Android Components like Activities
 */
data class ChooseComicBookResult(val paths: List<Uri>, val permissionFlags: Int)