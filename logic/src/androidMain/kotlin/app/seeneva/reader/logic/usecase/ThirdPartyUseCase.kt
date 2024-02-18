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

package app.seeneva.reader.logic.usecase

import android.content.Context
import app.seeneva.reader.common.coroutines.Dispatched
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.common.coroutines.io
import app.seeneva.reader.logic.R
import app.seeneva.reader.logic.entity.legal.ThirdParty
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.BufferedReader

interface ThirdPartyUseCase {
    /**
     * Load third party dependencies used by the application
     */
    suspend fun loadThirdParties(): List<ThirdParty>
}

internal class ThirdPartyUseCaseImpl(
    context: Context,
    override val dispatchers: Dispatchers,
    private val json: Json = Json
) : ThirdPartyUseCase, Dispatched {
    private val res = context.resources

    override suspend fun loadThirdParties(): List<ThirdParty> =
        io {
            // Get dependencies from Android raw file
            val dependenciesJson = res.openRawResource(R.raw.dependencies)
                .bufferedReader()
                .use(BufferedReader::readText)

            ensureActive()

            json.decodeFromString(ListSerializer(ThirdParty.serializer()), dependenciesJson)
                .sortedWith { a, b -> a.name.compareTo(b.name, true) }
        }
}