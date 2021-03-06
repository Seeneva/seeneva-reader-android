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

object RustBuildTypeFlavor {
    const val NAME = "rust_build_type"

    const val RUST_DEBUG = "rustDebug"
    const val RUST_RELEASE = "rustRelease"
}

object AppStoreFlavor {
    const val NAME = "app_store"

    const val GOOGLE_PLAY = "gplay"
    const val FDROID = "fdroid"
    const val GITHUB = "github"
}