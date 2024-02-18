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
import app.seeneva.reader.data.source.local.db.dao.ComicBookSource
import app.seeneva.reader.logic.entity.ComicInfo
import app.seeneva.reader.logic.mapper.ComicMetadataIntoComicInfo
import org.tinylog.kotlin.Logger

interface GetComicInfoUseCase {
    suspend fun byId(id: Long): ComicInfo?
}

internal class GetComicInfoUseCaseImpl(
    context: Context,
    private val comicBookSource: ComicBookSource,
    private val mapper: ComicMetadataIntoComicInfo,
    override val dispatchers: Dispatchers
) : GetComicInfoUseCase, Dispatched {
    private val context = context.applicationContext

    override suspend fun byId(id: Long) =
        io {
            try {
                mapper(comicBookSource.getFullById(id), context)
            } catch (t: Throwable) {
                Logger.error(t, "Can't get comic book info by id: $t")
                throw t
            }
        }
}