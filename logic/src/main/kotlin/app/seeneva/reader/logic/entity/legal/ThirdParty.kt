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

package app.seeneva.reader.logic.entity.legal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Describes single third party dependency used by the application
 * @param name name of the dependency
 * @param version used version of the dependency
 * @param authors authors of the dependency
 * @param summary short description of the dependency
 * @param homepage dependency home page URL
 * @param license describes dependency's license
 */
@Serializable
data class ThirdParty(
    val name: String,
    val version: String,
    val authors: String,
    val summary: String,
    val homepage: String,
    val license: ThirdPartyLicense
)

/**
 * Describes single third party dependency license
 * @param type type of the license
 * @param customTextFileName License full text file custom name
 */
@Serializable
data class ThirdPartyLicense(
    val type: License,
    @SerialName("custom_file") private val customTextFileName: String? = null
) {
    /**
     * License's full text file name
     */
    val textFileName
        get() = customTextFileName ?: type.id
}