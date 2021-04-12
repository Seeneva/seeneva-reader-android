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

package extension

import org.gradle.api.Project
import java.io.FileNotFoundException

typealias Properties = java.util.Properties

/**
 * Load [Properties] by file path
 * @param path properties file path. See [Project.file] for more info
 * @return parsed properties
 * @throws FileNotFoundException
 */
fun Project.loadProperties(path: Any): Properties {
    val propertiesFile = file(path)

    if (!propertiesFile.exists()) {
        throw FileNotFoundException()
    }

    return propertiesFile.bufferedReader().use { reader ->
        Properties().also { it.load(reader) }
    }
}