/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2023-2024 Sergei Solodovnikov
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

import com.android.build.api.dsl.SettingsExtension

// It should be on the top of the file
// https://docs.gradle.org/current/userguide/plugins.html#sec:plugin_management
pluginManagement {
    // Used versions. Change them to update!
    val androidVersion = "8.2.2"
    val androidMinSdk = 16
    val androidCompileSdk = 33
    val androidTargetSdk = androidCompileSdk

    settings.extra["androidVersion"] = androidVersion
    settings.extra["androidMinSdk"] = androidMinSdk
    settings.extra["androidCompileSdk"] = androidCompileSdk
    settings.extra["androidTargetSdk"] = androidTargetSdk

    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }

    plugins {
        id("com.android.settings") version androidVersion apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
        google()
    }

    versionCatalogs {
        register("libs") {
            val androidVersion: String by settings
            val androidMinSdk: Int by settings
            val androidCompileSdk: Int by settings
            val androidTargetSdk: Int by settings

            val kotlinVersion = "1.9.22"

            version("androidGradlePlugin", androidVersion)
            version("androidMinSdk", androidMinSdk.toString())
            version("androidCompileSdk", androidCompileSdk.toString())
            version("androidTargetSdk", androidTargetSdk.toString())

            version("kotlin", kotlinVersion)

            plugin(
                "android-application",
                "com.android.application"
            ).versionRef("androidGradlePlugin")

            plugin(
                "android-library",
                "com.android.library"
            ).versionRef("androidGradlePlugin")

            plugin(
                "ksp",
                "com.google.devtools.ksp"
            ).version("$kotlinVersion-1.0.17")

            library(
                "kotlin-test-junit",
                "org.jetbrains.kotlin", "kotlin-test-junit"
            ).version(kotlinVersion)
        }
    }
}

plugins {
    id("com.android.settings")
}

configure<SettingsExtension> {
    val androidMinSdk: Int by settings
    val androidCompileSdk: Int by settings

    ndkVersion = "21.4.7075529"
    buildToolsVersion = "34.0.0"

    minSdk = androidMinSdk
    compileSdk = androidCompileSdk
}

rootProject.name = "SeenevaReader"
rootProject.buildFileName = "build.gradle.kts"

include(":app", ":common", ":data", ":logic")