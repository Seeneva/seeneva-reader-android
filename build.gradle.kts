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

    id(Plugin.KSP) version PluginVersion.KSP apply false
    // https://docs.gradle.org/current/userguide/kotlin_dsl.html#sec:plugins_resolution_strategy
    id(Plugin.ANDROID_APPLICATION) version PluginVersion.ANDROID apply false
    id(Plugin.ANDROID_LIBRARY) version PluginVersion.ANDROID apply false
}

allprojects {
    // we will use ViewPager2, so remove ViewPager dependency globally
    configurations.configureEach {
        exclude(group = "androidx.viewpager")
    }
}

subprojects {
    apply {
        if (isAppLayer) {
            plugin(Plugin.ANDROID_APPLICATION)
        } else {
            plugin(Plugin.ANDROID_LIBRARY)
        }

        plugin("org.jetbrains.kotlin.android")
        plugin("org.jetbrains.kotlin.plugin.serialization")
    }

    configure<BaseExtension> {
        ndkVersion = "21.4.7075529"
        buildToolsVersion = "33.0.2"

        compileSdkVersion(33)

        defaultConfig {
            minSdk = 16
            targetSdk = 33

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

            multiDexEnabled = true
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            isCoreLibraryDesugaringEnabled = true

            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        sourceSets.all {
            java.srcDir("src/$name/kotlin")
        }

        // needed to build tests
        packagingOptions {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }

        lintOptions {
            // Translations can miss some strings especially after Weblate pull request
            // Lets consider this as a warning to finish 'lintRelease' Gradle task
            // Update defaultConfig.resConfigs to add new supported translations
            warning("MissingTranslation")
        }
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
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
        implementation(Deps.KOIN_ANDROIDX_SCOPE)

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
