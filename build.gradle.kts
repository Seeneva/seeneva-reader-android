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

import com.android.build.gradle.BaseExtension
import extension.loadProperties
import extension.requireEnvOrProperty
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version Version.KOTLIN
    kotlin("plugin.serialization") version Version.KOTLIN
    kotlin("android") version Version.KOTLIN apply false
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        mavenCentral()
        google()
        // will be deprecated soon
        jcenter()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:4.1.2")
        //classpath(kotlin("gradle-plugin", Version.KOTLIN))

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        // will be deprecated soon
        jcenter()
    }
}

subprojects {
    apply {
        if (isAppLayer) {
            plugin("com.android.application")
        } else {
            plugin("com.android.library")
        }
        plugin("org.jetbrains.kotlin.android")
        plugin("org.jetbrains.kotlin.plugin.serialization")
    }

    configure<BaseExtension> {
        //TODO update when fixed https://github.com/android/ndk/issues/1391 or build my own libz?
        ndkVersion = "21.4.7075529"
        buildToolsVersion = "30.0.3"
        compileSdkVersion(30)

        defaultConfig {
            minSdkVersion(16)
            targetSdkVersion(30)

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

            resConfigs("en", "ru")
            vectorDrawables.useSupportLibrary = true

            multiDexEnabled = true
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            isCoreLibraryDesugaringEnabled = true

            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        sourceSets.all {
            java.srcDir("src/$name/kotlin")
        }

        // needed to build tests
        packagingOptions {
            pickFirst("META-INF/AL2.0")
            pickFirst("META-INF/LGPL2.1")
        }

        lintOptions {
            // Translations can miss some strings especially after Weblate pull request
            // Lets consider this as a warning to finish 'lintRelease' Gradle task
            // Update defaultConfig.resConfigs to add new supported translations
            warning("MissingTranslation")
        }
    }

//    extensions.configure(AndroidExtensionsExtension::class) {
//        isExperimental = true
//    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    dependencies {
        // some Java 8 features
        // https://developer.android.com/studio/write/java8-support-table
        "coreLibraryDesugaring"(Deps.ANDROID_JAVA8_DESUGAR)

        implementation(Deps.KOTLINX_SERIALIZATION_JSON)
        implementation(Deps.KOTLINX_COROUTINES_ANDROID)

        implementation(Deps.ANDROIDX_ANNOTATIONS)
        implementation(Deps.ANDROIDX_CORE_KTX)

        //implementation(Deps.KOIN_ANDROID)
        implementation(Deps.KOIN_ANDROIDX_SCOPE) {
            exclude("androidx.viewpager")
        }

        implementation(Deps.TINYLOG_API)
        implementation(Deps.TINYLOG_IMPL)

        if (name != "common") {
            implementation(project(":common"))
        }

        testImplementation(TestDeps.KOTLINX_COROUTINES_TEST)
        testImplementation(Deps.KOTLINX_COROUTINES_CORE)
        testImplementation(kotlin("test-junit", Version.KOTLIN))
        testImplementation(TestDeps.ANDROIDX_TEST_JUNIT_KTX)

        testImplementation(TestDeps.MOCKK)
        testImplementation(TestDeps.KLUENT) {
            exclude("com.nhaarman.mockitokotlin2")
        }
        testImplementation(TestDeps.KOIN_TEST) {
            exclude("org.mockito")
        }

        "androidTestImplementation"(TestDeps.KOTLIN_FAKER)
        "androidTestImplementation"(TestDeps.KOIN_TEST)
        "androidTestImplementation"(TestDeps.KLUENT) {
            exclude("com.nhaarman.mockitokotlin2")
        }

        "androidTestImplementation"(TestDeps.ANDROIDX_TEST_RUNNER)
        "androidTestImplementation"(TestDeps.ANDROIDX_TEST_JUNIT_KTX)

        "androidTestImplementation"(TestDeps.KOTLINX_COROUTINES_TEST)
        "androidTestImplementation"(kotlin("test-junit", Version.KOTLIN))
    }
}

val Project.isAppLayer: Boolean
    get() = name == "app"

val Project.isDataLayer: Boolean
    get() = name == "data"
