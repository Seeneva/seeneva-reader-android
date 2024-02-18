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

package app.seeneva.reader.logic.entity.legal

import androidx.annotation.StringRes
import app.seeneva.reader.logic.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://youtrack.jetbrains.com/issue/KT-49110
private const val SPDX_GPL3_OR_LATER = "GPL-3.0-or-later"
private const val SPDX_APACHE2 = "Apache-2.0"
private const val SPDX_MIT = "MIT"
private const val SPDX_BSD2 = "BSD-2-Clause"
private const val SPDX_GPL2_CE = "GPL-2.0-with-classpath-exception"
private const val SPDX_ID_CC_BY_4 = "CC-BY-4.0"

private const val ID_PUBLIC_DOMAIN = "public_domain"
private const val ID_FREE = "free"

/**
 * Open source license types used by the application
 * @param id license id. e.g. SPDX Identifier (https://spdx.org/licenses/)
 * @param nameResId human readable license name
 */
@Serializable
enum class License(
    val id: String,
    @StringRes val nameResId: Int
) {
    @SerialName(SPDX_APACHE2)
    APACHE2(SPDX_APACHE2, R.string.license_apache2),

    @SerialName(SPDX_BSD2)
    BSD2(SPDX_BSD2, R.string.license_bsd2),

    @SerialName(SPDX_GPL2_CE)
    GPL2_CE(SPDX_GPL2_CE, R.string.license_gpl2_ce),

    @SerialName(SPDX_MIT)
    MIT(SPDX_MIT, R.string.license_mit),

    @SerialName(ID_PUBLIC_DOMAIN)
    PUBLIC_DOMAIN(ID_PUBLIC_DOMAIN, R.string.license_public_domain),

    @SerialName(ID_FREE)
    FREE(ID_FREE, R.string.license_free),

    @SerialName(SPDX_GPL3_OR_LATER)
    GPL3_OR_LATER(SPDX_GPL3_OR_LATER, R.string.license_gpl3_or_later),

    @SerialName(SPDX_ID_CC_BY_4)
    CC_BY_4(SPDX_ID_CC_BY_4, R.string.license_cc_by_4)
}