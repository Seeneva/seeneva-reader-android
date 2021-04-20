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

import extension.loadProperties

plugins {
    kotlin("kapt")
}

android {
    buildFeatures {
        viewBinding = true
    }

    splits {
        abi {
            isEnable = !hasProperty("seeneva.disableSplitApk")

            reset()

            include(*Abi.values().map { it.abiName }.toTypedArray())

            isUniversalApk = true
        }
    }

    signingConfigs {
        register("release") {
            // Add `keystore.properties` to provide data needed for app signing process:
            // storeFile=/path/to/keystore
            // storePassword=
            // keyAlias=
            // keyPassword=

            val keystoreProperties =
                runCatching { loadProperties("keystore.properties") }.getOrNull()

            /**
             * Get sign property by name. Try to find it in the `keystore.properties` or in all other place
             * @param name sign property name
             * @return sign property value
             */
            fun signProperty(name: String) =
                keystoreProperties?.getProperty(name) ?: property(name) as String

            storeFile = file(signProperty("storeFile"))
            storePassword = signProperty("storePassword")
            keyAlias = signProperty("keyAlias")
            keyPassword = signProperty("keyPassword")

            isV1SigningEnabled = true
            isV2SigningEnabled = true
        }
    }

    defaultConfig {
        applicationId = "app.seeneva.reader"

        resConfigs("en", "ru")
        vectorDrawables.useSupportLibrary = true

        missingDimensionStrategy(RustBuildTypeFlavor.NAME, RustBuildTypeFlavor.RUST_RELEASE)
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            ndk {
                // https://developer.android.com/studio/build/shrink-code.html#native-crash-support
                debugSymbolLevel = "symbol_table"
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs["release"]
        }
        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    flavorDimensions(AppStoreFlavor.NAME)

    productFlavors {
        register(AppStoreFlavor.DEFAULT) {
            dimension = AppStoreFlavor.NAME
        }

        register(AppStoreFlavor.FDROID) {
            dimension = AppStoreFlavor.NAME

            versionNameSuffix = "-fdroid"
            //TODO This will be used in future releases (e.g show donate button in the application)
        }
    }

    packagingOptions {
        // https://github.com/Kotlin/kotlinx.coroutines#avoiding-including-the-debug-infrastructure-in-the-resulting-apk
        exclude("DebugProbesKt.bin")
        // Not needed right now, but should return if I will use web connections
        exclude("okhttp3/**/publicsuffixes.gz")
    }

//    testOptions{
//        unitTests.setIncludeAndroidResources(true)
//    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(project(":logic")) {
        exclude("androidx.viewpager")
    }

    implementation(Deps.ANDROIDX_APPCOMPAT) {
        exclude("androidx.viewpager")
    }
    implementation(Deps.ANDROIDX_VIEW_PAGER) {
        exclude("androidx.viewpager")
    }
    implementation(Deps.ANDROIDX_RECYCLER_VIEW)
    implementation(Deps.ANDROIDX_RECYCLER_VIEW_SELECTION)
    implementation(Deps.ANDROIDX_FRAGMENT_KTX) {
        exclude("androidx.viewpager")
    }
    implementation(Deps.ANDROIDX_LIFECYCLE_SERVICE)
    implementation(Deps.ANDROIDX_LIFECYCLE_VIEWMODEL)
    implementation(Deps.ANDROIDX_LIFECYCLE_LIVEDATA)
    implementation(Deps.ANDROIDX_LIFECYCLE_JAVA8)
    implementation(Deps.ANDROIDX_PAGING_RUNTIME)
    implementation(Deps.ANDROIDX_CONSTRAINT_LAYOUT)
    implementation(Deps.ANDROIDX_WORK_RUNTIME)
    implementation(Deps.ANDROIDX_SWIPE_REFRESH_LAYOUT)

    implementation(Deps.MATERIAL) {
        exclude("androidx.viewpager")
    }

    implementation(Deps.KOIN_ANDROIDX_VIEWMODEL) {
        exclude("androidx.viewpager")
    }
    implementation(Deps.KOIN_ANDROIDX_WORKMANAGER)

    implementation(Deps.SCALE_IMAGE_VIEW)
}