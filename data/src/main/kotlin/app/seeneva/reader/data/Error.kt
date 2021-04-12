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

package app.seeneva.reader.data

import androidx.annotation.Keep

/**
 * Fatal errors. E.g. JNI errors
 */
@Suppress("unused")
class NativeFatalError @Keep constructor(message: String) : Error(message)

@Suppress("unused")
class NativeException @Keep constructor(val code: Int, message: String? = null) : RuntimeException(message) {
    companion object {
        @JvmStatic
        @Keep
        val CODE_CONTAINER_READ = 0

        /**
         * Known format, but it can't be used as a comic container
         */
        @JvmStatic
        @Keep
        val CODE_CONTAINER_OPEN_UNSUPPORTED = 1

        /**
         * Comic book archive doesn't contain any images
         */
        @JvmStatic
        @Keep
        val CODE_EMPTY_BOOK = 2

        /**
         * Can't open one or more image in the comic book archive
         */
        @JvmStatic
        @Keep
        val CODE_IMAGE_OPEN = 3

        /**
         * Can't find the file in the comic book container (e.g. comic page by it position)
         */
        @JvmStatic
        @Keep
        val CODE_CONTAINER_CANT_FIND_FILE = 4
    }
}

