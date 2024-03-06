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

import com.android.build.api.dsl.SigningConfig
import com.android.build.api.dsl.VariantDimension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.ApkVariantOutput
import com.android.build.gradle.api.ApplicationVariant
import extension.envOrProperty
import extension.loadProperties
import extension.requireEnvOrProperty
import extension.signProperties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")

    alias(libs.plugins.android.application)
}

kotlin {
    androidTarget {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))

            implementation(project(":logic"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.annotation)
            implementation(libs.androidx.core)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.activity)
            implementation(libs.androidx.viewpager)
            implementation(libs.androidx.recyclerview)
            implementation(libs.androidx.recyclerview.selection)
            implementation(libs.androidx.fragment)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.service)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.livedata)
            implementation(libs.androidx.lifecycle.java8)
            implementation(libs.androidx.paging.runtime)
            implementation(libs.androidx.constraintlayout)
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.swiperefreshlayout)
            implementation(libs.androidx.multidex)

            implementation(libs.kotlinx.coroutines.android)

            implementation(libs.google.android.material)

            implementation(libs.koin.androidx)
            implementation(libs.koin.androidx.viewmodel)
            implementation(libs.koin.androidx.workmanager)

            implementation(libs.scaleImageView)

            implementation(libs.tinylog.api)
            implementation(libs.tinylog.impl)
        }

        named("androidUnitTest") {
            dependencies {
                implementation(libs.kotlin.test.junit)

                implementation(libs.kluent.android)
            }
        }
    }
}

android {
    // True if this build is running in CI
    val buildUsingCI = !System.getenv("CI").isNullOrEmpty()

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    androidResources {
        generateLocaleConfig = true
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
            applyPropertiesSigning()
        }

        named("debug") {
            if (buildUsingCI) {
                // Allow override signing properties on build started by CI
                applyPropertiesSigning()
            }
        }
    }

    defaultConfig {
        applicationId = "app.seeneva.reader"
        namespace = "app.seeneva.reader"

        targetSdk = libs.versions.androidTargetSdk.get().toInt()

        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // allow to set app id suffix from properties. It is required by CI.
        applicationIdSuffix =
            envOrProperty(extension.ENV_APP_ID_SUFFIX, extension.PROP_APP_ID_SUFFIX)

        loadProperties(rootDir.resolve("seeneva.properties")).also { seenevaProperties ->
            versionCode = requireEnvOrProperty(
                extension.ENV_VERSION_CODE,
                extension.PROP_VERSION_CODE,
                seenevaProperties
            ).toInt()

            versionName = requireEnvOrProperty(
                extension.ENV_VERSION_NAME,
                extension.PROP_VERSION_NAME,
                seenevaProperties
            )
        }

        vectorDrawables.useSupportLibrary = true

        enableShowDonate(true)

        missingDimensionStrategy(RustBuildTypeFlavor.NAME, RustBuildTypeFlavor.RUST_RELEASE)
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = switchableSigningConfig("release")

            ndk {
                // https://developer.android.com/studio/build/shrink-code.html#native-crash-support
                debugSymbolLevel = if (hasProperty(extension.PROP_NO_DEB_SYMBOLS)) {
                    "none"
                } else {
                    "full"
                }
            }
        }

        named("debug") {
            isMinifyEnabled = false
            isDebuggable = true

            applicationIdSuffix = ".debug"

            signingConfig = switchableSigningConfig("debug")

            sourceSets {
                named("main") {
                    //setRoot("androidDebug")
                }
            }
        }
    }

    flavorDimensions += listOf(AppStoreFlavor.NAME)

    productFlavors {
        register(AppStoreFlavor.GOOGLE_PLAY) {
            dimension = AppStoreFlavor.NAME

            enableShowDonate(false)
        }

        register(AppStoreFlavor.FDROID) {
            dimension = AppStoreFlavor.NAME

            versionNameSuffix = "-fdroid"
        }
        register(AppStoreFlavor.GITHUB) {
            dimension = AppStoreFlavor.NAME

            versionNameSuffix = "-gh"
        }
    }

    packaging {
        resources.excludes += setOf(
            // https://github.com/Kotlin/kotlinx.coroutines#avoiding-including-the-debug-infrastructure-in-the-resulting-apk
            "DebugProbesKt.bin",
            // Not needed right now, but should return if I will use web connections
            "okhttp3/**/publicsuffixes.gz"
        )
    }

    if (buildUsingCI) {
        applicationVariants.configureEach(::configureOutputName)
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

//    testOptions{
//        unitTests.setIncludeAndroidResources(true)
//    }
}

dependencies {
    // some Java 8 features
    // https://developer.android.com/studio/write/java8-support-table
    "coreLibraryDesugaring"(libs.google.android.java.desugar)
}

/**
 * Allows conditionally disable output APK/AAB signing
 * @param name name of a sign config
 * @param enabled true if build signed output, unsigned otherwise
 */
fun BaseExtension.switchableSigningConfig(
    name: String,
    enabled: Boolean = !hasProperty(extension.PROP_BUILD_UNSIGNED)
) = if (enabled) {
    signingConfigs[name]
} else {
    null
}

/**
 * Apply signing params from the Java properties file or Gradle properties if properties file is not provided
 * @param propertiesFileName Java properties file which should be used
 */
fun SigningConfig.applyPropertiesSigning(propertiesFileName: String = "keystore.properties") {
    // Add `keystore.properties` to provide data needed for app signing process:
    // seeneva.storeFile=/path/to/keystore
    // seeneva.storePassword=
    // seeneva.keyAlias=
    // seeneva.keyPassword=

    val signProperties = signProperties(propertiesFileName) ?: return

    storeFile = file(signProperties[extension.PROP_STORE_FILE] as String).absoluteFile
    storePassword = signProperties[extension.PROP_STORE_PASS] as String
    keyAlias = signProperties[extension.PROP_KEY_ALIAS] as String
    keyPassword = signProperties[extension.PROP_KEY_PASS] as String
}

/**
 * Change is donate button enabled
 * @param enable true if donate button enabled
 */
fun VariantDimension.enableShowDonate(enable: Boolean) {
    buildConfigField("boolean", "DONATE_ENABLED", "$enable")
}

/**
 * Configure naming of output APKs
 * @param variant build variant to configure
 */
fun configureOutputName(variant: ApplicationVariant) {
    val abiSplitEnabled = android.splits.abi.isEnable

    variant.outputs
        .withType<ApkVariantOutput>()
        .configureEach {
            val outputFilters = filters

            outputFileName = buildString {
                append("seeneva-${variant.versionName}")

                if (abiSplitEnabled) {
                    append('-')
                    append(if (outputFilters.isEmpty()) {
                        "universal"
                    } else {
                        outputFilters.joinToString("-") { it.identifier }
                    })
                }

                if (variant.buildType.isDebuggable) {
                    append("-debug")
                }

                if (!variant.isSigningReady) {
                    append("-unsigned")
                }

                append(".apk")
            }
        }
}