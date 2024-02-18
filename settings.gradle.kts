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
        val kotlinVersion = "1.9.22"
        val kotlinCoroutinesVersion = "1.5.1"
        val koinVersion = "2.2.3"

        register("libs") {
            val androidVersion: String by settings
            val androidMinSdk: Int by settings
            val androidCompileSdk: Int by settings
            val androidTargetSdk: Int by settings

            version("androidGradlePlugin", androidVersion)
            version("androidMinSdk", androidMinSdk.toString())
            version("androidCompileSdk", androidCompileSdk.toString())
            version("androidTargetSdk", androidTargetSdk.toString())

            version("androidxLifecycle", "2.3.1")
            version("androidxPaging", "3.0.0")
            version("androidxRoom", "2.5.2")

            version("kotlin", kotlinVersion)
            version("tinylog", "2.3.1")
            version("cmake", "3.10.2")

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
                "androidx-annotation",
                "androidx.annotation", "annotation"
            ).version("1.2.0")

            library(
                "androidx-core",
                "androidx.core", "core-ktx"
            ).version("1.5.0")

            library(
                "androidx-appcompat",
                "androidx.appcompat", "appcompat"
            ).version("1.3.0")

            library(
                "androidx-activity",
                "androidx.activity", "activity-ktx"
            ).version("1.2.3")

            library(
                "androidx-viewpager",
                "androidx.viewpager2", "viewpager2"
            ).version("1.0.0")

            library(
                "androidx-recyclerview",
                "androidx.recyclerview", "recyclerview"
            ).version("1.2.0")
            library(
                "androidx-recyclerview-selection",
                "androidx.recyclerview", "recyclerview-selection"
            ).version("1.1.0")

            library(
                "androidx-fragment",
                "androidx.fragment", "fragment-ktx"
            ).version("1.3.4")

            library(
                "androidx-lifecycle-runtime",
                "androidx.lifecycle", "lifecycle-runtime-ktx"
            ).versionRef("androidxLifecycle")
            library(
                "androidx-lifecycle-service",
                "androidx.lifecycle", "lifecycle-service"
            ).versionRef("androidxLifecycle")
            library(
                "androidx-lifecycle-viewmodel",
                "androidx.lifecycle", "lifecycle-viewmodel-ktx"
            ).versionRef("androidxLifecycle")
            library(
                "androidx-lifecycle-livedata",
                "androidx.lifecycle", "lifecycle-livedata-ktx"
            ).versionRef("androidxLifecycle")
            library(
                "androidx-lifecycle-java8",
                "androidx.lifecycle", "lifecycle-common-java8"
            ).versionRef("androidxLifecycle")

            library(
                "androidx-room",
                "androidx.room", "room-ktx"
            ).versionRef("androidxRoom")
            library(
                "androidx-room-compiler",
                "androidx.room", "room-compiler"
            ).versionRef("androidxRoom")

            library(
                "androidx-paging-common",
                "androidx.paging", "paging-common-ktx"
            ).versionRef("androidxPaging")
            library(
                "androidx-paging-runtime",
                "androidx.paging", "paging-runtime-ktx"
            ).versionRef("androidxPaging")

            library(
                "androidx-constraintlayout",
                "androidx.constraintlayout", "constraintlayout"
            ).version("2.0.4")

            library(
                "androidx-work-runtime",
                "androidx.work", "work-runtime-ktx"
            ).version("2.7.1")

            library(
                "androidx-swiperefreshlayout",
                "androidx.swiperefreshlayout", "swiperefreshlayout"
            ).version("1.1.0")

            library(
                "androidx-multidex",
                "androidx.multidex", "multidex"
            ).version("2.0.1")

            library(
                "androidx-palette",
                "androidx.palette", "palette-ktx"
            ).version("1.0.0")

            library(
                "google-android-material",
                "com.google.android.material", "material"
            ).version("1.3.0")

            library(
                "google-android-java-desugar",
                "com.android.tools", "desugar_jdk_libs"
            ).version("2.0.3")

            library(
                "kotlinx-coroutines-core",
                "org.jetbrains.kotlinx", "kotlinx-coroutines-core"
            ).version(kotlinCoroutinesVersion)
            library(
                "kotlinx-coroutines-android",
                "org.jetbrains.kotlinx", "kotlinx-coroutines-android"
            ).version(kotlinCoroutinesVersion)

            library(
                "kotlinx-serialization-json",
                "org.jetbrains.kotlinx", "kotlinx-serialization-json"
            ).version("1.2.1")

            library(
                "koin-androidx",
                "io.insert-koin", "koin-androidx-scope"
            ).version(koinVersion)
            library(
                "koin-androidx-viewmodel",
                "io.insert-koin", "koin-androidx-viewmodel"
            ).version(koinVersion)
            library(
                "koin-androidx-workmanager",
                "io.insert-koin", "koin-androidx-workmanager"
            ).version(koinVersion)

            library(
                "coil",
                "io.coil-kt", "coil-base"
            ).version("1.2.1")

            library(
                "scaleImageView",
                "com.davemorrissey.labs", "subsampling-scale-image-view-androidx"
            ).version("3.10.0")

            library(
                "rTree",
                "com.github.davidmoten", "rtree2"
            ).version("0.9-RC1")

            library(
                "tinylog-api",
                "org.tinylog", "tinylog-api-kotlin"
            ).versionRef("tinylog")
            library(
                "tinylog-impl",
                "org.tinylog", "tinylog-impl"
            ).versionRef("tinylog")
        }

        register("testLibs") {
            library(
                "android-test-runner",
                "androidx.test", "runner"
            ).version("1.5.2")
            library(
                "android-test-junit",
                "androidx.test.ext", "junit-ktx"
            ).version("1.1.5")

            library(
                "kotlin-test-junit",
                "org.jetbrains.kotlin", "kotlin-test-junit"
            ).version(kotlinVersion)
            library(
                "kotlinx-coroutines-test",
                "org.jetbrains.kotlinx", "kotlinx-coroutines-test"
            ).version(kotlinCoroutinesVersion)

            library(
                "koin-test",
                "io.insert-koin", "koin-test"
            ).version(koinVersion)

            library(
                "faker",
                "io.github.serpro69", "kotlin-faker"
            ).version("1.7.1")

            library(
                "kluent-android",
                "org.amshove.kluent", "kluent-android"
            ).version("1.65")

            library(
                "mockk",
                "io.mockk", "mockk"
            ).version("1.11.0")
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