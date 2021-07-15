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

typealias PropKeys = Pair<String, String>

/**
 * Property: path to a keystore
 */
const val PROP_STORE_FILE = "seeneva.storeFile"

/**
 * Property: a keystore password
 */
const val PROP_STORE_PASS = "seeneva.storePassword"

/**
 * Property: a keystore alias
 */
const val PROP_KEY_ALIAS = "seeneva.keyAlias"

/**
 * Property: a key password
 */
const val PROP_KEY_PASS = "seeneva.keyPassword"

/**
 * Property: version code of the app
 */
const val PROP_VERSION_CODE = "seeneva.versionCode"

/**
 * Property: version name of the app
 */
const val PROP_VERSION_NAME = "seeneva.versionName"

/**
 * Property: disable generating debug symbols
 */
const val PROP_NO_DEB_SYMBOLS = "seeneva.noDebSymbols"

/**
 * Property: build unsigned Android APK/AAB
 */
const val PROP_BUILD_UNSIGNED = "seeneva.unsigned"

/**
 * Property: append additional application id suffix
 */
const val PROP_APP_ID_SUFFIX = "seeneva.applicationIdSuffix"


/**
 * Env variable: path to a keystore
 */
const val ENV_SEENEVA_STORE_FILE = "SEENEVA_STORE_FILE"

/**
 * Env variable: a keystore password
 */
const val ENV_SEENEVA_STORE_PASS = "SEENEVA_STORE_PASS"

/**
 * Env variable: a keystore alias
 */
const val ENV_SEENEVA_KEY_ALIAS = "SEENEVA_KEY_ALIAS"

/**
 * Env variable: a key password
 */
const val ENV_SEENEVA_KEY_PASS = "SEENEVA_KEY_PASS"

/**
 * Env variable: version code of the app
 */
const val ENV_VERSION_CODE = "SEENEVA_VERSION_CODE"

/**
 * Env variable: version name of the app
 */
const val ENV_VERSION_NAME = "SEENEVA_VERSION_NAME"

/**
 * Env variable: append additional application id suffix
 */
const val ENV_APP_ID_SUFFIX = "SEENEVA_APP_ID_SUFFIX"

/**
 * @param envName env variable name
 * @param propName property name
 * @param properties optional properties
 * @return env value or value from [properties] or project property if any
 */
fun Project.envOrProperty(
    envName: String,
    propName: String,
    properties: Properties? = null
): String? =
    (System.getenv(envName) ?: if (properties != null) properties[propName] else findProperty(
        propName
    )) as String?

/**
 * @see envOrProperty
 */
fun Project.requireEnvOrProperty(
    envName: String,
    propName: String,
    properties: Properties? = null
) = checkNotNull(envOrProperty(envName, propName, properties)) {
    "Can't get any value from Env by $envName or properties by $propName"
}

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

/**
 * Load sign [Properties] from file [path] or Gradle properties
 * @param path path to the sign *.properties file
 * @param keys optional map of properties/env keys
 * @return loaded sign properties from file or properties or null
 */
fun Project.signProperties(
    path: Any,
    keys: Array<PropKeys> = arrayOf(
        PROP_STORE_FILE to ENV_SEENEVA_STORE_FILE,
        PROP_STORE_PASS to ENV_SEENEVA_STORE_PASS,
        PROP_KEY_ALIAS to ENV_SEENEVA_KEY_ALIAS,
        PROP_KEY_PASS to ENV_SEENEVA_KEY_PASS
    )
): Properties? =
    runCatching { loadProperties(path) }
        .recover {
            val resultProperties = Properties()

            for ((propKey, envKey) in keys) {
                val v = findProperty(propKey) ?: System.getenv(envKey) ?: break

                resultProperties[propKey] = v
            }

            if (resultProperties.size == keys.size) {
                resultProperties
            } else {
                null
            }
        }.getOrNull()