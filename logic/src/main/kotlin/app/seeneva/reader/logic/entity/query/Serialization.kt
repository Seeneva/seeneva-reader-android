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

package app.seeneva.reader.logic.entity.query

import app.seeneva.reader.logic.entity.query.filter.FilterGroup
import app.seeneva.reader.logic.entity.query.filter.FilterProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*

/**
 * Serializer for [QueryParams]
 */
internal class QueryParamsSerializer(
    private val filtersProvider: FilterProvider
) : KSerializer<QueryParams> {
    private val filtersSerializer = ListSerializer(FilterData.serializer())

    override val descriptor = buildClassSerialDescriptor(QueryParams::class.java.name) {
        element<String>("sort")
        element("filters", filtersSerializer.descriptor)
    }

    override fun deserialize(decoder: Decoder) =
        decoder.decodeStructure(descriptor) {
            QueryParams.build {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        CompositeDecoder.DECODE_DONE -> break
                        INDEX_SORT -> sort =
                            decodeStringElement(descriptor, index).let { QuerySort.fromKey(it) }
                        INDEX_FILTERS -> {
                            addFilters(
                                decodeSerializableElement(
                                    descriptor,
                                    index,
                                    filtersSerializer
                                )
                            )
                        }
                        else -> throw SerializationException("Unexpected index $index")
                    }
                }
            }
        }

    override fun serialize(encoder: Encoder, value: QueryParams) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, INDEX_SORT, value.sort.key)
            encodeSerializableElement(
                descriptor,
                INDEX_FILTERS,
                filtersSerializer,
                value.filters.map { (filterGroupKey, filter) ->
                    FilterData(
                        filter.id,
                        filterGroupKey.name
                    )
                })
        }
    }

    private fun QueryParams.Builder.addFilters(filtersData: List<FilterData>) {
        if (filtersData.isEmpty()) {
            return
        }

        val filters =
            filtersProvider.groups()
                .associate { filterGroup -> filterGroup.id to filterGroup.filters.associateBy { it.id } }

        for ((id, groupId) in filtersData) {
            val realGroupId =
                runCatching { FilterGroup.ID.valueOf(groupId) }.getOrNull() ?: continue

            val filter = filters[realGroupId]?.get(id) ?: continue

            addFilter(realGroupId, filter)
        }
    }

    @Serializable
    private data class FilterData(
        @SerialName("id")
        val id: String,
        @SerialName("group_id")
        val groupId: String
    )

    companion object {
        private const val INDEX_SORT = 0
        private const val INDEX_FILTERS = 1
    }
}

