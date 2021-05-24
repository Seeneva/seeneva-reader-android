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

package app.seeneva.reader.screen.list.selection

import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.widget.RecyclerView
import app.seeneva.reader.screen.list.adapter.ComicsAdapter

class ComicKeyProvider(private val adapter: ComicsAdapter) : ItemKeyProvider<Long>(SCOPE_CACHED) {
    override fun getKey(position: Int) =
        // Adapter.getItem will trigger page loading. We don't need this.
        adapter.peek(position)?.id

    override fun getPosition(key: Long): Int {
        // With AndroidX Paging 3.0.0 where is no stable ids anymore. Adapter.getItemId will always return RecyclerView.NO_POSITION

        // Snapshot has all loaded items plus placeholders before and after (null objects)
        val snapshot = adapter.snapshot()

        // It is better to iterate over loaded pages only excluding placeholders
        // Paging library allows to set how many objects will be cached in case if users have really large libraries
        return snapshot.items
            .withIndex()
            .firstNotNullOfOrNull { (i, item) ->
                if (item.id == key) {
                    i + snapshot.placeholdersBefore
                } else {
                    null
                }
            } ?: RecyclerView.NO_POSITION
    }
}