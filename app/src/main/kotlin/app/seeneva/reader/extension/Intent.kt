/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2024 Sergei Solodovnikov
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

package app.seeneva.reader.extension

import android.content.Intent
import androidx.core.content.IntentCompat
import java.io.Serializable

/**
 * Kotlin wrapper around [IntentCompat.getSerializableExtra]
 *
 * @param key extra's key
 */
inline fun <reified T : Serializable> Intent.getSerializableExtraCompat(key: String?): T? =
    IntentCompat.getSerializableExtra(this, key, T::class.java)